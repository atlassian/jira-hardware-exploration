package com.atlassian.performance.tools.hardware

import com.amazonaws.services.ec2.model.InstanceType.C518xlarge
import com.amazonaws.services.ec2.model.InstanceType.M44xlarge
import com.amazonaws.services.s3.transfer.TransferManagerBuilder
import com.atlassian.performance.tools.aws.api.Aws
import com.atlassian.performance.tools.hardware.aws.HardwareRuntime
import com.atlassian.performance.tools.jiraactions.api.*
import com.atlassian.performance.tools.jirasoftwareactions.api.actions.ViewBacklogAction
import com.atlassian.performance.tools.jvmtasks.api.TaskTimer.time
import com.atlassian.performance.tools.lib.Apdex
import com.atlassian.performance.tools.lib.LogConfigurationFactory
import com.atlassian.performance.tools.lib.ScoredActionMetric
import com.atlassian.performance.tools.lib.readResult
import com.atlassian.performance.tools.lib.s3cache.S3Cache
import com.atlassian.performance.tools.lib.table.GenericPlainTextTable
import com.atlassian.performance.tools.report.api.StandardTimeline
import com.atlassian.performance.tools.report.api.result.EdibleResult
import com.atlassian.performance.tools.report.api.result.RawCohortResult
import com.atlassian.performance.tools.workspace.api.TestWorkspace
import org.apache.logging.log4j.core.config.ConfigurationFactory
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Paths
import java.time.Duration


class HardwareApdexAnalysisIT {

    private val cacheKey = "QUICK-132-fix-v3"
    private val workspace = HardwareRuntime.rootWorkspace.isolateTask(cacheKey)

    // define the Apdex sets we want to investigate
    private val hardwareOfInterest: Hardware = Hardware(C518xlarge, 7, M44xlarge)

    // The following needs to match the configuration of the test run you want to analyze
    // Taken from extraLarge.Kt hold + ramp + flat values
    // This is done manulally rather than by using an instance of ExtraLarge etc because they require a Jira Licence file.
    private val testDuration: Duration = Duration.ZERO + Duration.ofSeconds(90) + Duration.ofMinutes(20)

    @Before
    fun setup() {
        ConfigurationFactory.setConfigurationFactory(LogConfigurationFactory(HardwareRuntime.rootWorkspace.currentTask))
    }

    @Test
    fun shouldAnalyzeApdex() {

        // https://docs.oracle.com/javase/tutorial/essential/io/fileOps.html#glob
        val searchPatternRoot = "**/${hardwareOfInterest.jira}/nodes/${hardwareOfInterest.nodeCount}/dbs/${hardwareOfInterest.db}"

        // action-metrics.jpt contain the apdex information
        // status.txt is needed to pass TestWorkspace.readResult
        val downloadFileFilter = "$searchPatternRoot/**/{action-metrics.jpt,status.txt}"
        val cache = getWorkspaceCache(HardwareRuntime.prepareAws(), downloadFileFilter)

        // get the files
        time("download") { cache.download() }
        val runs = workspace.directory.toFile().walkTopDown()
            .filter { file ->
                val actionMetricFileFilter = "$searchPatternRoot/**runs/[0-9]*"
                matchesSearchPattern(file.absolutePath, actionMetricFileFilter)
            }
            .filter { file ->
                // ignore any empty run/# folders or ones that only contain a status.txt which indicates a failed run
                file.listFiles().any { it.name != "status.txt" }
            }
            .mapNotNull { file ->
                apdexMetrics(file)
            }

        if (runs.none()) {
            println("Unable to run comparison no apdex calculated")
            return
        }

        // get the best worst runs as defined by the overall apdex
        val sortedRuns = runs
            .toList()
            .sortedByDescending { it.apdex }

        val best = sortedRuns.firstOrNull()
        val worst = sortedRuns.lastOrNull()

        if (best == null) {
            println("Unable to run comparison 'best' is undefined")
            return
        }

        if (worst == null) {
            println("Unable to run comparison 'worst' is undefined")
            return
        }

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
        table.addRow(listOf("", "Run", "Apdex", "Total Actions", "Error %", "Satisfactory %", "Tolerable %"))

        // data
        tableAddRunSummaryRow(table, best, "best")
        tableAddRunSummaryRow(table, worst, "worst")

        println(table.generate())
    }

    private fun tableAddRunSummaryRow(table: GenericPlainTextTable, results: ApdexResults, name: String) {
        val eventTot = results.metrics.size
        val errorPerc = results.metrics.filter { it.actionMetric.result == ActionResult.ERROR }.size * 100.0 / eventTot
        val satisfactoryPerc = results.metrics.filter { it.score == 1.0f }.size * 100.0 / eventTot
        val tolerablePerc = results.metrics.filter { it.score == 0.5f }.size * 100.0 / eventTot
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
        table.addRow(listOf("Action", "Run", "Apdex", "Total", "Profile", "Error %", "Satisfactory %", "Tolerable %"))

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
                if (worstRow != null) {
                    tableAddActionRow(table, worst.run, worst.metrics.size, "", worstRow.value)
                } else {
                    table.addRowEmpty("?")
                }

                table.addRowEmpty()

            }

        println(table.generate())
    }

    private fun tableAddActionRow(table: GenericPlainTextTable, run: Int, allActionTotal: Int, actionLabel: String, metrics: List<ScoredActionMetric>) {

        val actionTotal = metrics.size
        val apdex = String.format("%.2f", Apdex().averageAllScoredMetrics(metrics))
        val ok = metrics.filter { it.actionMetric.result == ActionResult.OK }.size
        val error = metrics.filter { it.actionMetric.result == ActionResult.ERROR }.size
        val errorPerc = String.format("%.2f", error * 100.0 / actionTotal)
        val satisfactory = metrics.filter { it.score == 1.0f }.size
        val satisfactoryPerc = String.format("%.2f", satisfactory * 100.0 / actionTotal)
        val tolerable = metrics.filter { it.score == 0.5f }.size
        val tolerablePerc = String.format("%.2f", tolerable * 100.0 / actionTotal)
        val frustrated = metrics.filter { it.score == 0.0f && it.actionMetric.result == ActionResult.OK }.size
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

    private fun apdexMetrics(targetFile: File): ApdexResults? {

        val run = targetFile.name
        val workspace = TestWorkspace(targetFile.toPath())

        println("Processing $targetFile")

        return targetFile.listFiles()
            .filter { file ->
                // I think this is a linear folder structure only ever 1 cohort per run? So potentially this could be simpler
                val cohortFilter = "**/*${hardwareOfInterest.jira}*"
                matchesSearchPattern(file.absolutePath, cohortFilter)
            }
            .mapNotNull { file ->
                calculateApdexResults(workspace, file, run)
            }
            .firstOrNull()
    }

    private fun calculateApdexResults(workspace: TestWorkspace, file: File, run: String): ApdexResults? {
        println("Calculate Apdex for ${file.name}")
        val cohortResult = workspace.readResult(file.name)
        val failure = cohortResult.failure
        return if (failure == null) {

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
            val apdexStandard = Apdex().score(metrics)

            // keep the per-action apdex as score this applied to OK and ERROR actionMetrics as we want to keep the ERRORs on hand for later use
            val scoredMetrics = Apdex().scoreEachMetric(metrics)

            // filter out the ERRORs before re-calculating apdex
            val apdexFromScoredMetrics = Apdex().averageAllScoredMetrics(scoredMetrics.filter { it.actionMetric.result == ActionResult.OK })

            // ensure our new calculation matches the original style
            if (apdexStandard != apdexFromScoredMetrics) {
                throw Exception("apdex standard $apdexStandard != apdex from scored metrics $apdexFromScoredMetrics")
            }

            ApdexResults(run.toInt(), apdexStandard, scoredMetrics)

        } else {
            println("Unable to calculate Apdex for ${file.name}! ${failure.localizedMessage}")
            Assert.fail("Nothing to calculate!")
            null
        }
    }

    private fun getWorkspaceCache(aws: Aws, searchPattern: String = "**/action-metrics.jpt") = S3Cache(
        transfer = TransferManagerBuilder.standard()
            .withS3Client(aws.s3)
            .build(),
        bucketName = "quicksilver-jhwr-cache-ireland",
        cacheKey = cacheKey,
        localPath = workspace.directory,
        etags = HardwareRuntime.rootWorkspace.directory.resolve(".etags"),
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

    private class ApdexResults(val run: Int, val apdex: Double, val metrics: List<ScoredActionMetric>) {
        override fun toString(): String = "run:$run apdex:$apdex"
    }
}
