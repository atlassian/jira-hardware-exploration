package com.atlassian.performance.tools.hardware

import com.amazonaws.services.ec2.model.InstanceType
import com.amazonaws.services.ec2.model.InstanceType.*
import com.amazonaws.services.s3.transfer.TransferManagerBuilder
import com.atlassian.performance.tools.hardware.IntegrationTestRuntime.aws
import com.atlassian.performance.tools.hardware.IntegrationTestRuntime.logContext
import com.atlassian.performance.tools.hardware.IntegrationTestRuntime.taskName
import com.atlassian.performance.tools.hardware.IntegrationTestRuntime.workspace
import com.atlassian.performance.tools.hardware.failure.BugAwareTolerance
import com.atlassian.performance.tools.jiraactions.api.*
import com.atlassian.performance.tools.jirasoftwareactions.api.actions.ViewBacklogAction
import com.atlassian.performance.tools.jvmtasks.api.TaskTimer.time
import com.atlassian.performance.tools.lib.Apdex
import com.atlassian.performance.tools.lib.table.GenericPlainTextTable
import com.atlassian.performance.tools.lib.WeightedActionMetric
import com.atlassian.performance.tools.lib.readResult
import com.atlassian.performance.tools.lib.s3cache.S3Cache
import com.atlassian.performance.tools.report.api.StandardTimeline
import com.atlassian.performance.tools.report.api.result.EdibleResult
import com.atlassian.performance.tools.report.api.result.RawCohortResult
import com.atlassian.performance.tools.workspace.api.TestWorkspace
import org.apache.logging.log4j.Logger
import org.junit.Test
import java.io.File
import java.time.Duration
import java.nio.file.FileSystems
import java.nio.file.Paths


class HardwareApdexAnalysisIT {

    private val logger: Logger = logContext.getLogger(this::class.java.canonicalName)

    // define the appdex sets we want to investigate
    private val jiraInstanceTypeOfInterest: InstanceType = C518xlarge
    private val nodeCountOfInterest: Int = 7

    // The following needs to match the configuration of the test run you want to analyze
    // Taken from extraLarge.Kt hold + ramp + flat values
    private val testDuration: Duration = Duration.ZERO + Duration.ofSeconds(90) + Duration.ofMinutes(20)

    @Test
    fun shouldAnalyzeAppdex() {

        // https://docs.oracle.com/javase/tutorial/essential/io/fileOps.html#glob
        // action-metrics.jpt contain the appdex information
        // status.txt is needed to pass TestWorkspace.readResult
        val searchPattern = "**/$jiraInstanceTypeOfInterest/nodes/$nodeCountOfInterest/**/{action-metrics.jpt,status.txt}"
        val cache = getWorkspaceCache(searchPattern)

        // get the files
        time("download") { cache.download() }
        val runs = workspace.directory.toFile().walkTopDown()
            .filter { file ->
                matchesSearchPattern(file.absolutePath,"**/nodes/$nodeCountOfInterest/**runs/[0-9]*")
            }
            .filter { file ->
                // ignore any empty run/# folders or ones that only contain a status.txt which indicates a failed run
                file.listFiles().any{ it.name != "status.txt" }
            }
            .map { file ->
                apdexMetrics(file)
            }
            .toList()

        // get the best worst runs as defined by the overall appdex
        val sortedRuns = runs
            .sortedByDescending { it.apdex }
        val best = sortedRuns.first()
        val worst = sortedRuns.last()

        // comparison
        compare(best, worst)
    }

    private fun compare(best: ApdexResults, worst: ApdexResults) {
        // summary
        compareRuns(best, worst)

        // detail
        compareActionEventByLabel(best, worst)
    }

    private fun compareRuns(best: ApdexResults, worst: ApdexResults) {
        val table = GenericPlainTextTable()

        // header
        table.addRow(listOf( "", "Run", "Apdex", "Total Actions", "Error %", "Satisfactory %", "Tolerable %"))

        // data
        tableAddRunSummaryRow(table, best, "best")
        tableAddRunSummaryRow(table, worst, "worst")

        println(table.generate())
    }

    private fun tableAddRunSummaryRow(table: GenericPlainTextTable, results: ApdexResults, name: String) {
        val eventTot = results.metrics.size
        val errorPerc = results.metrics.filter { it.actionMetric.result == ActionResult.ERROR }.size * 100.0 / eventTot
        val satisfactoryPerc = results.metrics.filter { it.weight == 1.0f }.size * 100.0 / eventTot
        val tolerablePerc = results.metrics.filter { it.weight == 0.5f }.size * 100.0 / eventTot
        table.addRow(listOf(
            name,
            "${results.run}",
            "${results.apdex}",
            "$eventTot",
            String.format("%.3f", errorPerc),
            String.format("%.3f", satisfactoryPerc),
            String.format("%.3f", tolerablePerc)
            ))
    }

    private fun compareActionEventByLabel(best: ApdexResults, worst: ApdexResults) {

        val table = GenericPlainTextTable()

        // header
        table.addRow(listOf( "Action", "Run", "Apdex", "Total", "Profile", "Error %", "Satisfactory %", "Tolerable %"))

        // data
        best.metrics
            .asSequence()
            //.map { it.actionMetric }
            .groupBy { it.actionMetric.label }
            .asSequence()
            .sortedBy { it.key }
            .forEach { entry ->
                tableAddActionRow(table, best.run, best.metrics.size, entry.key, entry.value)

                //find the equivalent row form thw worst run
                val worstRow = worst.metrics.asSequence()
                    .groupBy { it.actionMetric.label }
                    .asSequence().singleOrNull {
                        it.key == entry.key
                    }
                if(worstRow != null) {
                    tableAddActionRow(table, worst.run, worst.metrics.size, "", worstRow.value)
                } else {
                    table.addRowEmpty("?")
                }

                table.addRowEmpty()

            }

        println(table.generate())
    }

    private fun tableAddActionRow(table: GenericPlainTextTable, run: Int, allActionTotal: Int, actionLabel: String, metrics: List<WeightedActionMetric>) {

        val actionTotal = metrics.size
        val apdex = String.format("%.2f", Apdex().average(metrics))
        val ok = metrics.filter { it.actionMetric.result == ActionResult.OK }.size
        val error = metrics.filter { it.actionMetric.result == ActionResult.ERROR }.size
        val errorPerc = String.format("%.2f", error * 100.0 / actionTotal)
        val satisfactory = metrics.filter { it.weight == 1.0f }.size
        val satisfactoryPerc = String.format("%.2f", satisfactory * 100.0 / actionTotal)
        val tolerable = metrics.filter { it.weight == 0.5f }.size
        val tolerablePerc = String.format("%.2f", tolerable * 100.0 / actionTotal)
        val frustrated = metrics.filter { it.weight == 0.0f && it.actionMetric.result == ActionResult.OK }.size
        val profilePerc = String.format("%.2f", actionTotal * 100.0 / allActionTotal)

        // double check our sums add up
        if (satisfactory + tolerable + frustrated != ok) {
            throw Exception("Calculated sum of SATISFACTORY ($satisfactory) + TOLERABLE ($tolerable) + FRUSTRATED ($frustrated) != calculated OK $ok")
        }

        if (ok + error != actionTotal) {
            throw Exception("Calculated sum of OK ($ok) + ERROR ($error) != calculated TOTAL $actionTotal")
        }

        table.addRow(listOf(actionLabel, "$run", apdex, "$actionTotal", profilePerc, errorPerc, satisfactoryPerc, tolerablePerc))
    }

    private fun apdexMetrics(targetFile: File): ApdexResults {

        val run = targetFile.name
        val workspace = TestWorkspace(targetFile.toPath())

        val cohortResults = targetFile.listFiles()
            .filter { file ->
                // I think this is a linear folder structure only ever 1 cohort per run? So potentially this could be simpler
                val cohortFilter = "**/*$jiraInstanceTypeOfInterest*"
                matchesSearchPattern(file.absolutePath, cohortFilter)
            }
            .map { file ->

                val cohortResult = workspace.readResult(file.name)
                val failure = cohortResult.failure
                if (failure == null) {
                    
                    val postProcessedResult = processResults(cohortResult)

                    val cohort = postProcessedResult.cohort
                    if (postProcessedResult.failure != null) {
                        throw Exception("$cohort failed", postProcessedResult.failure)
                    }

                    // reproduce the apdex calculation from HardwareExploration
                    val labels = listOf(
                        ViewBacklogAction.VIEW_BACKLOG,
                        VIEW_BOARD,
                        VIEW_ISSUE,
                        VIEW_DASHBOARD,
                        SEARCH_WITH_JQL,
                        ADD_COMMENT_SUBMIT,
                        CREATE_ISSUE_SUBMIT,
                        EDIT_ISSUE_SUBMIT,
                        PROJECT_SUMMARY,
                        BROWSE_PROJECTS,
                        BROWSE_BOARDS
                    ).map { it.label }
                    val metrics = postProcessedResult.actionMetrics.filter { it.label in labels }
                    val apdex = Apdex().score(metrics)

                    // keep the per-action apdex as 'weight' this applied to OK and ERROR actionMetrics as we want to keep the ERRORs on hand for later use
                    val weightedMetrics = Apdex().weight(metrics)

                    // filter out the ERRORs before re-calculating apdex
                    val apdexW = Apdex().average(weightedMetrics.filter { it.actionMetric.result == ActionResult.OK })

                    // ensure our new calculation matches the original style
                    if(apdex != apdexW) {
                        throw Exception("apdex $apdex != apdexW $apdexW")
                    }

                    ApdexResults(run.toInt(), apdex, weightedMetrics)

                } else {
                    println("ignominious failure! ${failure.localizedMessage}")
                    BugAwareTolerance(logger).handle(failure, workspace)
                    null
                }
            }
            // this seems very hacky, I should just be able to get 1 result out of the sequence code above ... ?
            .first{ it is ApdexResults}

        return cohortResults!!
    }

    private fun getWorkspaceCache(searchPattern: String = "**/action-metrics.jpt") = S3Cache(
        transfer = TransferManagerBuilder.standard()
            .withS3Client(aws.s3)
            .build(),
        bucketName = "quicksilver-jhwr-cache-ireland",
        cacheKey = taskName,
        localPath = workspace.directory,
        searchPattern = searchPattern
    )

    private fun matchesSearchPattern(location: String, searchPattern: String): Boolean {
        val matcher = FileSystems.getDefault().getPathMatcher("glob:$searchPattern")
        val path = Paths.get(location)
        return matcher.matches(path)
    }

    private fun processResults(
        rawResults: RawCohortResult
    ): EdibleResult = synchronized(this) {
        val timeline = StandardTimeline(testDuration)
        return rawResults.prepareForJudgement(timeline)
    }

    private class ApdexResults(val run: Int, val apdex: Double, val metrics: List<WeightedActionMetric>)
    {
        override fun toString(): String = "run:$run apdex:$apdex"
    }
}
