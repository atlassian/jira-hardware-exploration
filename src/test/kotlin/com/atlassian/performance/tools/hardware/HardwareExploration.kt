package com.atlassian.performance.tools.hardware

import com.amazonaws.services.ec2.model.InstanceType
import com.atlassian.performance.tools.aws.api.Aws
import com.atlassian.performance.tools.aws.api.Investment
import com.atlassian.performance.tools.awsinfrastructure.api.InfrastructureFormula
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.EbsEc2Instance
import com.atlassian.performance.tools.awsinfrastructure.api.jira.DataCenterFormula
import com.atlassian.performance.tools.awsinfrastructure.api.loadbalancer.ElasticLoadBalancerFormula
import com.atlassian.performance.tools.awsinfrastructure.api.storage.JiraSoftwareStorage
import com.atlassian.performance.tools.awsinfrastructure.api.virtualusers.MulticastVirtualUsersFormula
import com.atlassian.performance.tools.hardware.vu.CustomScenario
import com.atlassian.performance.tools.infrastructure.api.app.Apps
import com.atlassian.performance.tools.infrastructure.api.browser.chromium.Chromium69
import com.atlassian.performance.tools.infrastructure.api.jira.JiraLaunchTimeouts
import com.atlassian.performance.tools.infrastructure.api.jira.JiraNodeConfig
import com.atlassian.performance.tools.infrastructure.api.profiler.AsyncProfiler
import com.atlassian.performance.tools.infrastructure.api.splunk.DisabledSplunkForwarder
import com.atlassian.performance.tools.io.api.dereference
import com.atlassian.performance.tools.io.api.directories
import com.atlassian.performance.tools.jiraactions.api.*
import com.atlassian.performance.tools.jiraperformancetests.api.ProvisioningPerformanceTest
import com.atlassian.performance.tools.jirasoftwareactions.api.actions.ViewBacklogAction.Companion.VIEW_BACKLOG
import com.atlassian.performance.tools.lib.*
import com.atlassian.performance.tools.lib.infrastructure.ThrottlingMulticastVirtualUsersFormula
import com.atlassian.performance.tools.report.api.FullReport
import com.atlassian.performance.tools.report.api.StandardTimeline
import com.atlassian.performance.tools.report.api.result.CohortResult
import com.atlassian.performance.tools.report.api.result.EdibleResult
import com.atlassian.performance.tools.report.api.result.RawCohortResult
import com.atlassian.performance.tools.virtualusers.api.browsers.HeadlessChromeBrowser
import com.atlassian.performance.tools.virtualusers.api.config.VirtualUserBehavior
import com.atlassian.performance.tools.workspace.api.TaskWorkspace
import com.atlassian.performance.tools.workspace.api.TestWorkspace
import com.atlassian.performance.tools.workspace.api.git.GitRepo
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.io.File
import java.time.Duration
import java.util.concurrent.*

class HardwareExploration(
    private val scale: ApplicationScale,
    private val guidance: ExplorationGuidance,
    private val investment: Investment,
    private val aws: Aws,
    private val task: TaskWorkspace
) {

    private val virtualUsers: VirtualUserBehavior = VirtualUserBehavior.Builder(CustomScenario::class.java)
        .load(scale.load)
        .seed(78432)
        .diagnosticsLimit(32)
        .browser(HeadlessChromeBrowser::class.java)
        .build()
    private val awsParallelism = 6
    private val results = ConcurrentHashMap<Hardware, Future<HardwareExplorationResult>>()
    private val cache = HardwareExplorationResultCache(task.directory.resolve("result-cache.json"))
    private val logger: Logger = LogManager.getLogger(this::class.java)

    fun exploreHardware() {
        val space = guidance.instanceTypes.flatMap { instanceType ->
            (1..guidance.maxNodeCount).map { Hardware(instanceType, it) }
        }
        val awsExecutor = Executors.newFixedThreadPool(awsParallelism)
        val explorationExecutor = Executors.newFixedThreadPool(space.size)
        try {
            exploreHardwareInParallel(space, explorationExecutor, awsExecutor)
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
    ) {
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
    }

    private fun report(
        results: List<HardwareExplorationResult>
    ) = synchronized(this) {
        cache.write(results)
        HardwareExplorationTable().summarize(
            results = results,
            instanceTypesOrder = guidance.instanceTypes,
            table = task.isolateReport("exploration-table.csv")
        )
        HardwareExplorationChart(GitRepo.findFromCurrentDirectory()).plot(
            results = results,
            application = scale.description,
            output = task.isolateReport("exploration-chart.html"),
            instanceTypeOrder = compareBy { guidance.instanceTypes.indexOf(it) }
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
        return (1..resultCount).mapNotNull {
            val nextCompleted = completion.take()
            val hardware = inferHardware(nextCompleted)
            try {
                val result = nextCompleted.get()
                if (result.decision.worthExploring) {
                    tested++
                    logger.info("Finished $hardware")
                } else {
                    skipped++
                    logger.info("Skipped testing $hardware")
                }
                resultsSoFar += result
                report(resultsSoFar)
                result
            } catch (e: Exception) {
                failed++
                logger.error("Failed when testing $hardware", e)
                null
            } finally {
                val remaining = resultCount - tested - failed - skipped
                logger.info("#$it: TESTED: $tested, SKIPPED: $skipped, FAILED: $failed, REMAINING: $remaining")
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
        val decision = decideTesting(hardware, awsExecutor, completion)
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

    private fun decideTesting(
        hardware: Hardware,
        awsExecutor: ExecutorService,
        completion: ExecutorCompletionService<HardwareExplorationResult>
    ): HardwareExplorationDecision {
        if (hardware.nodeCount <= guidance.minNodeCountForAvailability) {
            return HardwareExplorationDecision(
                hardware = hardware,
                worthExploring = true,
                reason = "high availability"
            )
        }
        val smallerHardwareTests = (1 until hardware.nodeCount)
            .map { Hardware(hardware.instanceType, it) }
            .map { smallerHardware ->
                results.computeIfAbsent(smallerHardware) {
                    completion.submit(explore(
                        hardware = it,
                        awsExecutor = awsExecutor,
                        completion = completion
                    ))
                }
            }
        val smallerHardwareResults = try {
            smallerHardwareTests.map { it.get() }
        } catch (e: Exception) {
            return HardwareExplorationDecision(
                hardware = hardware,
                worthExploring = false,
                reason = "testing smaller hardware had failed, ERROR: ${e.message}"
            )
        }
        val apdexIncrements = smallerHardwareResults
            .asSequence()
            .mapNotNull { it.testResult }
            .sortedBy { it.hardware.nodeCount }
            .map { it.apdex }
            .zipWithNext { a, b -> b - a }
            .toList()
        val strongPositiveImpact = apdexIncrements.all { it > guidance.minApdexGain }
        return if (strongPositiveImpact) {
            HardwareExplorationDecision(
                hardware = hardware,
                worthExploring = true,
                reason = "adding more nodes made enough positive impact on Apdex"
            )
        } else {
            HardwareExplorationDecision(
                hardware = hardware,
                worthExploring = false,
                reason = "adding more nodes did not improve Apdex enough"
            )
        }
    }

    private fun getRobustResult(
        hardware: Hardware,
        executor: ExecutorService
    ): HardwareTestResult {
        val reusableResults = reuseResults(hardware)
        val missingResultCount = guidance.repeats - reusableResults.size
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
            guidance.pastFailures.handle(failure, workspace)
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
    ): EdibleResult {
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
        val hardwareResult = HardwareTestResult(
            hardware = hardware,
            apdex = Apdex().score(metrics),
            apdexSpread = 0.0,
            httpThroughput = AccessLogThroughput().gauge(workspace.digOutTheRawResults(cohort)),
            httpThroughputSpread = Throughput.ZERO,
            results = listOf(results),
            errorRate = ErrorRate().measure(metrics),
            errorRateSpread = 0.0
        )
        if (hardwareResult.errorRate > guidance.maxErrorRate) {
            throw Exception("Error rate for $cohort is too high: ${ErrorRate().measure(metrics)}")
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
        ).runAsync(
            workspace,
            executor,
            virtualUsers
        ).thenApply {
            @Suppress("DEPRECATION") val raw = CohortResult.toRawCohortResult(it)
            workspace.writeStatus(raw)
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
            jiraFormula = DataCenterFormula(
                apps = Apps(emptyList()),
                application = JiraSoftwareStorage("7.13.0"),
                jiraHomeSource = scale.dataset.jiraHomeSource,
                database = scale.dataset.database,
                configs = (1..hardware.nodeCount).map {
                    JiraNodeConfig.Builder()
                        .name("jira-node-$it")
                        .profiler(AsyncProfiler())
                        .launchTimeouts(
                            JiraLaunchTimeouts.Builder()
                                .initTimeout(Duration.ofMinutes(7))
                                .build()
                        )
                        .build()
                },
                loadBalancerFormula = ElasticLoadBalancerFormula(),
                computer = EbsEc2Instance(hardware.instanceType)
            ),
            virtualUsersFormula = ThrottlingMulticastVirtualUsersFormula(
                MulticastVirtualUsersFormula(
                    nodes = scale.vuNodes,
                    shadowJar = dereference("jpt.virtual-users.shadow-jar"),
                    splunkForwarder = DisabledSplunkForwarder(),
                    browser = Chromium69()
                )
            ),
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
            .map { it.scalePeriod(throughputUnit) }
            .map { it.count }
        val errorRates = results.map { it.errorRate }
        val testResult = HardwareTestResult(
            hardware = hardware,
            apdex = apdexes.average(),
            apdexSpread = apdexes.spread(),
            httpThroughput = Throughput(throughputs.average(), throughputUnit),
            httpThroughputSpread = Throughput(throughputs.spread(), throughputUnit),
            results = results.flatMap { it.results },
            errorRate = errorRates.average(),
            errorRateSpread = errorRates.spread()
        )
        val postProcessedResults = results.flatMap { it.results }.map { postProcess(it) }
        reportRaw("sub-test-comparison", postProcessedResults, hardware)
        if (testResult.apdexSpread > guidance.maxApdexSpread) {
            throw Exception("Apdex spread for $hardware is too big: ${apdexes.spread()}. Results: $results")
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
}

class ExplorationGuidance(
    val instanceTypes: List<InstanceType>,
    val maxNodeCount: Int,
    val minNodeCountForAvailability: Int,
    val repeats: Int,
    val minApdexGain: Double,
    val maxApdexSpread: Double,
    val maxErrorRate: Double,
    val pastFailures: FailureTolerance
)

interface FailureTolerance {
    fun handle(failure: Exception, workspace: TestWorkspace)
}

class LoggingFailureTolerance(
    private val logger: Logger
) : FailureTolerance {

    override fun handle(failure: Exception, workspace: TestWorkspace) {
        logger.error("Failure in $workspace, better investigate or remove it")
    }
}

class ThrowingFailureTolerance : FailureTolerance {
    override fun handle(failure: Exception, workspace: TestWorkspace) {
        throw Exception("Failed in $workspace", failure)
    }
}

class CleaningFailureTolerance : FailureTolerance {
    override fun handle(failure: Exception, workspace: TestWorkspace) {
        workspace.directory.toFile().deleteRecursively()
    }
}
