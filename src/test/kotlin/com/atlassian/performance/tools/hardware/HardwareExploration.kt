package com.atlassian.performance.tools.hardware

import com.atlassian.performance.tools.aws.api.Aws
import com.atlassian.performance.tools.aws.api.Investment
import com.atlassian.performance.tools.awsinfrastructure.api.InfrastructureFormula
import com.atlassian.performance.tools.awsinfrastructure.api.TargetingVirtualUserOptions
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.EbsEc2Instance
import com.atlassian.performance.tools.awsinfrastructure.api.jira.DataCenterFormula
import com.atlassian.performance.tools.awsinfrastructure.api.loadbalancer.ElasticLoadBalancerFormula
import com.atlassian.performance.tools.awsinfrastructure.api.virtualusers.MulticastVirtualUsersFormula
import com.atlassian.performance.tools.hardware.failure.FailureTolerance
import com.atlassian.performance.tools.hardware.guidance.ExplorationGuidance
import com.atlassian.performance.tools.hardware.tuning.JiraNodeTuning
import com.atlassian.performance.tools.hardware.vu.CustomScenario
import com.atlassian.performance.tools.infrastructure.api.distribution.ProductDistribution
import com.atlassian.performance.tools.infrastructure.api.jira.JiraNodeConfig
import com.atlassian.performance.tools.infrastructure.api.profiler.AsyncProfiler
import com.atlassian.performance.tools.io.api.dereference
import com.atlassian.performance.tools.io.api.directories
import com.atlassian.performance.tools.jiraactions.api.*
import com.atlassian.performance.tools.jiraperformancetests.api.ProvisioningPerformanceTest
import com.atlassian.performance.tools.jirasoftwareactions.api.actions.ViewBacklogAction.Companion.VIEW_BACKLOG
import com.atlassian.performance.tools.lib.*
import com.atlassian.performance.tools.lib.infrastructure.BestEffortProfiler
import com.atlassian.performance.tools.lib.infrastructure.PatientChromium69
import com.atlassian.performance.tools.lib.infrastructure.WgetOracleJdk
import com.atlassian.performance.tools.lib.s3cache.S3Cache
import com.atlassian.performance.tools.report.api.FullReport
import com.atlassian.performance.tools.report.api.StandardTimeline
import com.atlassian.performance.tools.report.api.result.EdibleResult
import com.atlassian.performance.tools.report.api.result.RawCohortResult
import com.atlassian.performance.tools.virtualusers.api.TemporalRate
import com.atlassian.performance.tools.virtualusers.api.VirtualUserOptions
import com.atlassian.performance.tools.virtualusers.api.browsers.HeadlessChromeBrowser
import com.atlassian.performance.tools.virtualusers.api.config.VirtualUserBehavior
import com.atlassian.performance.tools.virtualusers.api.config.VirtualUserTarget
import com.atlassian.performance.tools.workspace.api.TaskWorkspace
import com.atlassian.performance.tools.workspace.api.TestWorkspace
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.io.File
import java.net.URI
import java.time.Duration
import java.util.concurrent.*

class HardwareExploration(
    private val product: ProductDistribution,
    private val scale: ApplicationScale,
    private val guidance: ExplorationGuidance,
    private val investment: Investment,
    private val tuning: JiraNodeTuning,
    private val aws: Aws,
    private val task: TaskWorkspace,
    private val repeats: Int,
    private val pastFailures: FailureTolerance,
    private val errorRateWarningThreshold: Double,
    private val apdexSpreadWarningThreshold: Double,
    private val cache: S3Cache
) {
    private val awsParallelism = 6
    private val results = ConcurrentHashMap<Hardware, Future<HardwareExplorationResult>>()
    private val logger: Logger = LogManager.getLogger(this::class.java)

    fun exploreHardware(): List<HardwareExplorationResult> {
        val space = guidance.space()
        if (space.isEmpty()) {
            return emptyList()
        }
        val awsExecutor = Executors.newFixedThreadPool(awsParallelism)
        val explorationExecutor = Executors.newFixedThreadPool(space.size)
        try {
            return exploreHardwareInParallel(space, explorationExecutor, awsExecutor)
        } finally {
            explorationExecutor.shutdown()
            awsExecutor.shutdown()
            explorationExecutor.awaitTermination(70, TimeUnit.MINUTES)
        }
    }

    private fun exploreHardwareInParallel(
        hardwareSpace: List<Hardware>,
        explorationExecutor: ExecutorService,
        awsExecutor: ExecutorService
    ): List<HardwareExplorationResult> {
        val completion = ExecutorCompletionService<HardwareExplorationResult>(explorationExecutor)
        hardwareSpace.forEach { hardware ->
            results.computeIfAbsent(hardware) {
                completion.submit(explore(
                    hardware,
                    awsExecutor,
                    completion
                ))
            }
        }
        val completedResults = awaitResults(completion)
        report(completedResults)
        return completedResults
    }

    private fun report(
        results: List<HardwareExplorationResult>
    ) {
        guidance.report(
            results,
            task,
            scale.description
        )
    }

    private fun awaitResults(
        completion: ExecutorCompletionService<HardwareExplorationResult>
    ): List<HardwareExplorationResult> {
        val resultCount = results.size
        var tested = 0
        var skipped = 0
        var failed = 0
        logger.info("Awaiting $resultCount results")
        val resultsSoFar = mutableListOf<HardwareExplorationResult>()
        return (1..resultCount).mapNotNull { number ->
            val nextCompleted = completion.take()
            val hardware = inferHardware(nextCompleted)
            try {
                val result = nextCompleted.get()
                resultsSoFar += result
                report(resultsSoFar)
                if (result.decision.worthExploring) {
                    tested++
                    logger.info("Finished $hardware")
                } else {
                    skipped++
                    logger.info("Skipped testing $hardware")
                }
                return@mapNotNull result
            } catch (e: Exception) {
                failed++
                logger.error("Failed when testing $hardware", e)
                return@mapNotNull null
            } finally {
                val remaining = resultCount - tested - failed - skipped
                logger.info("#$number: TESTED: $tested, SKIPPED: $skipped, FAILED: $failed, REMAINING: $remaining")
            }
        }
    }

    private fun inferHardware(
        futureResult: Future<HardwareExplorationResult>
    ): Hardware = results
        .filterValues { it == futureResult }
        .keys
        .singleOrNull()
        ?: throw Exception("Cannot find the hardware for $futureResult without risking an exception")

    private fun explore(
        hardware: Hardware,
        awsExecutor: ExecutorService,
        completion: ExecutorCompletionService<HardwareExplorationResult>
    ): Callable<HardwareExplorationResult> = Callable {
        val decision = guidance.decideTesting(hardware) { otherHardware ->
            if (otherHardware == hardware) {
                throw Exception(
                    "Avoiding an infinite loop!" +
                        " Tried to obtain $hardware results in order to know if we want to obtain these results."
                )
            }
            results.computeIfAbsent(otherHardware) {
                completion.submit(explore(
                    hardware = it,
                    awsExecutor = awsExecutor,
                    completion = completion
                ))
            }
        }
        if (decision.worthExploring) {
            HardwareExplorationResult(
                decision = decision,
                testResult = getRobustResult(hardware, awsExecutor)
            )
        } else {
            HardwareExplorationResult(
                decision = decision,
                testResult = null
            )
        }
    }

    private fun getRobustResult(
        hardware: Hardware,
        executor: ExecutorService
    ): HardwareTestResult {
        val reusableResults = reuseResults(hardware)
        val missingResultCount = repeats - reusableResults.size
        val freshResults = runFreshResults(hardware, missingResultCount, executor)
        val allResults = reusableResults + freshResults
        return coalesce(allResults, hardware)
    }

    private fun reuseResults(
        hardware: Hardware
    ): List<HardwareTestResult> {
        val reusableResults = listPreviousRuns(hardware).mapNotNull { reuseResult(hardware, it) }
        if (reusableResults.isNotEmpty()) {
            logger.debug("Reusing ${reusableResults.size} results")
        }
        return reusableResults
    }

    private fun reuseResult(
        hardware: Hardware,
        previousRun: File
    ): HardwareTestResult? {
        val workspace = TestWorkspace(previousRun.toPath())
        val cohortResult = workspace.readResult(hardware.nameCohort(workspace))
        val failure = cohortResult.failure
        return if (failure == null) {
            score(hardware, cohortResult, workspace)
        } else {
            pastFailures.handle(failure, workspace)
            null
        }
    }

    private fun listPreviousRuns(
        hardware: Hardware
    ): List<File> {
        val hardwareDirectory = hardware
            .isolateRuns(task)
            .directory
            .toFile()
        return if (hardwareDirectory.isDirectory) {
            hardwareDirectory.directories()
        } else {
            emptyList()
        }
    }

    private fun postProcess(
        rawResults: RawCohortResult
    ): EdibleResult = synchronized(this) {
        val timeline = StandardTimeline(scale.load.total)
        return rawResults.prepareForJudgement(timeline)
    }

    private fun score(
        hardware: Hardware,
        results: RawCohortResult,
        workspace: TestWorkspace
    ): HardwareTestResult {
        val postProcessedResult = postProcess(results)
        val cohort = postProcessedResult.cohort
        if (postProcessedResult.failure != null) {
            throw Exception("$cohort failed", postProcessedResult.failure)
        }
        val labels = listOf(
            VIEW_BACKLOG,
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
        val throughput = AccessLogThroughput().gauge(workspace.digOutTheRawResults(cohort))
        val errorRate = ErrorRate().measure(metrics)
        val hardwareResult = HardwareTestResult(
            hardware = hardware,
            apdex = apdex,
            apdexes = listOf(apdex),
            httpThroughput = throughput,
            httpThroughputs = listOf(throughput),
            results = listOf(results),
            errorRate = errorRate,
            errorRates = listOf(errorRate)
        )
        if (hardwareResult.errorRate > errorRateWarningThreshold) {
            logger.warn("Error rate for $cohort is too high: $errorRate")
        }
        return hardwareResult
    }

    private fun runFreshResults(
        hardware: Hardware,
        missingResultCount: Int,
        executor: ExecutorService
    ): List<HardwareTestResult> {
        if (missingResultCount <= 0) {
            return emptyList()
        }
        logger.debug("Running $missingResultCount tests to get the rest of the results for $hardware")
        val nextResultNumber = chooseNextRunNumber(hardware)
        val newRuns = nextResultNumber.until(nextResultNumber + missingResultCount)
        val workspace = hardware.isolateRuns(task).directory
        return newRuns
            .map { workspace.resolve(it.toString()) }
            .map { TestWorkspace(it) }
            .map { testHardware(hardware, it, executor) }
            .map { it.get() }
    }

    private fun chooseNextRunNumber(
        hardware: Hardware
    ): Int = listPreviousRuns(hardware)
        .map { it.name }
        .mapNotNull { it.toIntOrNull() }
        .max()
        ?.plus(1)
        ?: 1

    private fun testHardware(
        hardware: Hardware,
        workspace: TestWorkspace,
        executor: ExecutorService
    ): CompletableFuture<HardwareTestResult> {
        return dataCenter(
            cohort = hardware.nameCohort(workspace),
            hardware = hardware
        ).executeAsync(
            workspace,
            executor,
            ScaleVirtualUserOptions(scale)
        ).thenApply { raw ->
            workspace.writeStatus(raw)
            cache.upload(workspace.directory.toFile())
            return@thenApply score(hardware, raw, workspace)
        }
    }

    private fun dataCenter(
        cohort: String,
        hardware: Hardware
    ): ProvisioningPerformanceTest = ProvisioningPerformanceTest(
        cohort = cohort,
        infrastructureFormula = InfrastructureFormula(
            investment = investment,
            jiraFormula = DataCenterFormula.Builder(
                productDistribution = product,
                jiraHomeSource = scale.dataset.dataset.jiraHomeSource,
                database = scale.dataset.dataset.database
            )
                .configs((1..hardware.nodeCount).map { nodeNumber ->
                    JiraNodeConfig.Builder()
                        .name("jira-node-$nodeNumber")
                        .profiler(BestEffortProfiler(AsyncProfiler()))
                        .jdk(WgetOracleJdk())
                        .build()
                        .let { tuning.tune(it, hardware, scale) }
                })
                .loadBalancerFormula(ElasticLoadBalancerFormula())
                .computer(EbsEc2Instance(hardware.jira).withVolumeSize(300))
                .databaseComputer(EbsEc2Instance(hardware.db).withVolumeSize(300))
                .adminUser(scale.dataset.adminLogin)
                .adminPwd(scale.dataset.adminPassword)
                .build(),
            virtualUsersFormula = MulticastVirtualUsersFormula.Builder(
                nodes = scale.vuNodes,
                shadowJar = dereference("jpt.virtual-users.shadow-jar")
            )
                .browser(PatientChromium69())
                .build(),
            aws = aws
        )
    )

    private fun coalesce(
        results: List<HardwareTestResult>,
        hardware: Hardware
    ): HardwareTestResult {
        val apdexes = results.map { it.apdex }
        val throughputUnit = Duration.ofSeconds(1)
        val throughputs = results
            .map { it.httpThroughput }
            .map { it.scaleTime(throughputUnit) }
            .map { it.change }
        val errorRates = results.map { it.errorRate }
        val testResult = HardwareTestResult(
            hardware = hardware,
            apdex = apdexes.average(),
            apdexes = results.flatMap { it.apdexes },
            httpThroughput = TemporalRate(throughputs.average(), throughputUnit),
            httpThroughputs = results.flatMap { it.httpThroughputs },
            results = results.flatMap { it.results },
            errorRate = errorRates.average(),
            errorRates = results.flatMap { it.errorRates }
        )
        val postProcessedResults = results.flatMap { it.results }.map { postProcess(it) }
        reportRaw("sub-test-comparison", postProcessedResults, hardware)
        val apdexSpread = apdexes.spread()
        if (apdexSpread > apdexSpreadWarningThreshold) {
            logger.warn("Apdex spread for $hardware is too big: $apdexSpread. Results: $results")
        }
        return testResult
    }

    private fun Iterable<Double>.spread() = max()!! - min()!!

    private fun reportRaw(
        reportName: String,
        results: List<EdibleResult>,
        hardware: Hardware
    ) {
        val workspace = hardware.isolateSubTask(task, reportName)
        FullReport().dump(
            results = results,
            workspace = TestWorkspace(workspace.directory)
        )
    }

    private class ScaleVirtualUserOptions(
        private val scale: ApplicationScale
    ) : TargetingVirtualUserOptions {
        override fun target(
            jira: URI
        ): VirtualUserOptions = VirtualUserOptions(
            target = VirtualUserTarget(
                webApplication = jira,
                userName = scale.dataset.adminLogin,
                password = scale.dataset.adminPassword
            ),
            behavior = VirtualUserBehavior.Builder(CustomScenario::class.java)
                .load(scale.load)
                .createUsers(true)
                .seed(78432)
                .diagnosticsLimit(32)
                .browser(HeadlessChromeBrowser::class.java)
                .createUsers(true)
                .skipSetup(true)
                .build()
        )
    }
}
