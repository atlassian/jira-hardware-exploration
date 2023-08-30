package com.atlassian.performance.tools.hardware

import com.atlassian.performance.tools.aws.api.Aws
import com.atlassian.performance.tools.aws.api.Investment
import com.atlassian.performance.tools.awsinfrastructure.api.InfrastructureFormula
import com.atlassian.performance.tools.awsinfrastructure.api.TargetingVirtualUserOptions
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.EbsEc2Instance
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.Volume
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
import com.atlassian.performance.tools.jiraperformancetests.api.ProvisioningPerformanceTest
import com.atlassian.performance.tools.lib.OverallError
import com.atlassian.performance.tools.lib.Ratio
import com.atlassian.performance.tools.lib.concurrency.submitWithLogContext
import com.atlassian.performance.tools.lib.infrastructure.BestEffortProfiler
import com.atlassian.performance.tools.lib.infrastructure.PatientChromium69
import com.atlassian.performance.tools.lib.infrastructure.S3HostedJdk
import com.atlassian.performance.tools.lib.readResult
import com.atlassian.performance.tools.lib.s3cache.S3Cache
import com.atlassian.performance.tools.lib.writeStatus
import com.atlassian.performance.tools.report.api.FullReport
import com.atlassian.performance.tools.report.api.result.EdibleResult
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
    private val requirements: OutcomeRequirements,
    private val investment: Investment,
    private val tuning: JiraNodeTuning,
    private val aws: Aws,
    private val task: TaskWorkspace,
    private val repeats: Int,
    private val pastFailures: FailureTolerance,
    private val metric: HardwareMetric,
    private val s3Cache: S3Cache,
    private val explorationCache: HardwareExplorationResultCache
) {
    private val awsParallelism = 8
    private val results = ConcurrentHashMap<Hardware, Future<HardwareExplorationResult>>()
    private val failures = CopyOnWriteArrayList<Exception>()
    private val logger: Logger = LogManager.getLogger(this::class.java)

    fun exploreHardware(): ReportedExploration {
        val space = guidance.space()
        if (space.isEmpty()) {
            return ReportedExploration(emptyList(), emptyList())
        }
        val awsExecutor = Executors.newFixedThreadPool(awsParallelism)
        val explorationExecutor = Executors.newFixedThreadPool(space.size)
        try {
            val result = exploreHardwareInParallel(space, explorationExecutor, awsExecutor)
            if (failures.isNotEmpty()) {
                val exception = Exception("One or more tests failed")
                failures.forEach { exception.addSuppressed(it) }
                throw exception
            }
            return result
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
    ): ReportedExploration {
        val completion = ExecutorCompletionService<HardwareExplorationResult>(explorationExecutor)
        hardwareSpace.forEach { hardware ->
            results.computeIfAbsent(hardware) {
                completion.submitWithLogContext("explore $hardware") {
                    explore(
                        hardware,
                        awsExecutor,
                        completion
                    )
                }
            }
        }
        val completedResults = awaitResults(completion)
        val report = report(completedResults)
        return ReportedExploration(completedResults, report)
    }

    private fun report(
        results: List<HardwareExplorationResult>
    ): List<File> {
        return guidance.report(
            results,
            requirements,
            task,
            scale.description,
            explorationCache
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
                failures.add(e)
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
    ): HardwareExplorationResult {
        val decision = guidance.decideTesting(hardware) { otherHardware ->
            if (otherHardware == hardware) {
                throw Exception(
                    "Avoiding an infinite loop!" +
                            " Tried to obtain $hardware results in order to know if we want to obtain these results."
                )
            }
            results.computeIfAbsent(otherHardware) {
                completion.submitWithLogContext("explore $it to decide on $hardware") {
                    explore(
                        hardware = it,
                        awsExecutor = awsExecutor,
                        completion = completion
                    )
                }
            }
        }
        return if (decision.worthExploring) {
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
            metric.score(hardware, cohortResult)
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
            s3Cache.upload(workspace.directory.toFile())
            return@thenApply metric.score(hardware, raw)
        }
    }

    private fun dataCenter(
        cohort: String,
        hardware: Hardware
    ): ProvisioningPerformanceTest = ProvisioningPerformanceTest(
        cohort = cohort,
        infrastructureFormula = InfrastructureFormula.Builder(
            aws, MulticastVirtualUsersFormula.Builder(
                nodes = scale.vuNodes,
                shadowJar = dereference("jpt.virtual-users.shadow-jar")
            )
                .browser(PatientChromium69())
                .build()
        ).jiraFormula(
            DataCenterFormula.Builder(
                productDistribution = product,
                jiraHomeSource = scale.dataset.dataset.jiraHomeSource,
                database = scale.dataset.dataset.database
            )
                .configs((1..hardware.nodeCount).map { nodeNumber ->
                    JiraNodeConfig.Builder()
                        .name("jira-node-$nodeNumber")
                        .profiler(BestEffortProfiler(AsyncProfiler()))
                        .jdk(S3HostedJdk())
                        .build()
                        .let { tuning.tune(it, hardware, scale) }
                })
                .loadBalancerFormula(ElasticLoadBalancerFormula())
                .computer(EbsEc2Instance(hardware.jira)).jiraVolume(Volume(300))
                .databaseComputer(EbsEc2Instance(hardware.db)).databaseVolume(Volume(300))
                .build()
        )
            .investment(investment)
            .build()
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
        val errorRates = results.map { it.overallError }
        val testResult = HardwareTestResult(
            hardware = hardware,
            apdex = apdexes.average(),
            apdexes = results.flatMap { it.apdexes },
            httpThroughput = TemporalRate(throughputs.average(), throughputUnit),
            httpThroughputs = results.flatMap { it.httpThroughputs },
            results = results.flatMap { it.results },
            overallError = OverallError(Ratio(errorRates.map { it.ratio.proportion }.average())),
            overallErrors = results.flatMap { it.overallErrors },
            maxActionError = results.mapNotNull { it.maxActionError }.maxBy { it.ratio }!!,
            maxActionErrors = results.flatMap { it.maxActionErrors ?: emptyList() }
        )
        val postProcessedResults = results.flatMap { it.results }.map { metric.postProcess(it) }
        reportRaw(postProcessedResults, hardware)
        return testResult
    }

    private fun reportRaw(
        results: List<EdibleResult>,
        hardware: Hardware
    ) {
        val workspace = hardware.isolateSubTask(task, "sub-test-comparison")
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
                .skipSetup(true)
                .build()
        )
    }
}
