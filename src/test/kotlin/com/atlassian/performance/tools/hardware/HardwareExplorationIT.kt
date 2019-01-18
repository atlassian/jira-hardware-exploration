package com.atlassian.performance.tools.hardware

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain
import com.amazonaws.regions.Regions
import com.amazonaws.services.ec2.model.InstanceType.C52xlarge
import com.atlassian.performance.tools.aws.api.Aws
import com.atlassian.performance.tools.aws.api.Investment
import com.atlassian.performance.tools.aws.api.StorageLocation
import com.atlassian.performance.tools.aws.api.TextCapacityMediator
import com.atlassian.performance.tools.awsinfrastructure.api.DatasetCatalogue
import com.atlassian.performance.tools.awsinfrastructure.api.InfrastructureFormula
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.EbsEc2Instance
import com.atlassian.performance.tools.awsinfrastructure.api.jira.DataCenterFormula
import com.atlassian.performance.tools.awsinfrastructure.api.loadbalancer.ElasticLoadBalancerFormula
import com.atlassian.performance.tools.awsinfrastructure.api.storage.JiraSoftwareStorage
import com.atlassian.performance.tools.awsinfrastructure.api.virtualusers.MulticastVirtualUsersFormula
import com.atlassian.performance.tools.hardware.vu.CustomScenario
import com.atlassian.performance.tools.infrastructure.api.app.Apps
import com.atlassian.performance.tools.infrastructure.api.browser.Browser
import com.atlassian.performance.tools.infrastructure.api.browser.chromium.Chromium69
import com.atlassian.performance.tools.infrastructure.api.dataset.Dataset
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
import com.atlassian.performance.tools.report.api.FullReport
import com.atlassian.performance.tools.report.api.StandardTimeline
import com.atlassian.performance.tools.report.api.result.CohortResult
import com.atlassian.performance.tools.report.api.result.EdibleResult
import com.atlassian.performance.tools.virtualusers.api.VirtualUserLoad
import com.atlassian.performance.tools.virtualusers.api.browsers.HeadlessChromeBrowser
import com.atlassian.performance.tools.virtualusers.api.config.VirtualUserBehavior
import com.atlassian.performance.tools.workspace.api.RootWorkspace
import com.atlassian.performance.tools.workspace.api.TestWorkspace
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.apache.logging.log4j.core.config.ConfigurationFactory
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.URI
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletableFuture.supplyAsync
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class HardwareExplorationIT {

    private val investment = Investment(
        useCase = "Test hardware recommendations",
        lifespan = Duration.ofHours(1) + Duration.ofMinutes(20)
    )
    private val load = VirtualUserLoad(
        virtualUsers = 200,
        ramp = Duration.ofSeconds(30),
        flat = Duration.ofMinutes(20)
    )
    private val virtualUsers = VirtualUserBehavior.Builder(CustomScenario::class.java)
        .load(load)
        .seed(78432)
        .diagnosticsLimit(32)
        .browser(HeadlessChromeBrowser::class.java)
        .build()

    private val browser: Browser = Chromium69()
    private val root: RootWorkspace = RootWorkspace()
    private val task = root.isolateTask("QUICK-8")
    private val repeats = 2
    private val awsParallelism = 8
    private val results = mutableMapOf<Hardware, CompletableFuture<HardwareTestResult>>()
    private lateinit var logger: Logger
    private lateinit var aws: Aws
    private lateinit var awsExecutor: ExecutorService

    @Before
    fun setUp() {
        ConfigurationFactory.setConfigurationFactory(LogConfigurationFactory(task))
        logger = LogManager.getLogger(this::class.java)
        aws = Aws(
            credentialsProvider = DefaultAWSCredentialsProviderChain(),
            region = Regions.EU_WEST_1,
            regionsWithHousekeeping = listOf(Regions.EU_WEST_1),
            capacity = TextCapacityMediator(Regions.EU_WEST_1),
            batchingCloudformationRefreshPeriod = Duration.ofSeconds(20)
        )
        awsExecutor = Executors.newFixedThreadPool(awsParallelism)
    }

    @After
    fun tearDown() {
        awsExecutor.shutdownNow()
    }

    @Test
    fun shouldExploreHardware() {
        val instanceTypes = listOf(
            C52xlarge
        )
        for (instanceType in instanceTypes) {
            for (nodeCount in 1..2) {
                val hardware = Hardware(instanceType, nodeCount)
                if (shouldWeScaleHorizontally(hardware)) {
                    results[hardware] = supplyAsync { getRobustResult(hardware) }
                } else {
                    break
                }
            }
        }
        results.forEach { _, futureResult -> futureResult.get() }
        summarize()
    }

    private fun shouldWeScaleHorizontally(
        hardware: Hardware
    ): Boolean {
        if (hardware.nodeCount < 4) {
            return true
        }
        val previousResults = results
            .filterKeys { it.instanceType == hardware.instanceType }
            .values
            .map { it.get() }
            .sortedBy { it.hardware.nodeCount }
        if (previousResults.last().apdex >= 0.50) {
            return false
        }
        val apdexIncrements = previousResults
            .map { it.apdex }
            .zipWithNext { a, b -> a - b }
        val canMoreNodesHelp = apdexIncrements.all { it > -0.02 }
        return if (canMoreNodesHelp) {
            true
        } else {
            logger.info(
                "We're not testing $hardware" +
                    ", because previous results shown a decrease in Apdex: $apdexIncrements" +
                    ", previous results: $previousResults"
            )
            false
        }
    }

    private fun getRobustResult(
        hardware: Hardware
    ): HardwareTestResult {
        val reusableResults = reuseResults(hardware)
        val missingResultCount = repeats - reusableResults.size
        val freshResults = runFreshResults(hardware, missingResultCount)
        val allResults = reusableResults + freshResults
        val robustResult = coalesce(allResults, hardware)
        logger.info("The robust result for $hardware is $robustResult")
        return robustResult
    }

    private fun reuseResults(
        hardware: Hardware
    ): List<HardwareTestResult> {
        val reusableResults = listPreviousRuns(hardware).mapNotNull { reuseResult(hardware, it) }
        if (reusableResults.isNotEmpty()) {
            logger.debug("Reusing results: $reusableResults")
        }
        return reusableResults
    }

    private fun reuseResult(
        hardware: Hardware,
        previousRun: File
    ): HardwareTestResult? {
        val workspace = TestWorkspace(previousRun.toPath())
        val cohortResult = workspace.readResult(hardware.nameCohort(workspace))
        val edibleResult = postProcess(cohortResult)
        return if (edibleResult.failure == null) {
            score(hardware, edibleResult, workspace)
        } else {
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
        rawResults: CohortResult
    ): EdibleResult {
        val timeline = StandardTimeline(load.total)
        return rawResults.prepareForJudgement(timeline)
    }

    private fun score(
        hardware: Hardware,
        results: EdibleResult,
        workspace: TestWorkspace
    ): HardwareTestResult {
        val cohort = results.cohort
        if (results.failure != null) {
            throw Exception("$cohort failed", results.failure)
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
        val metrics = results.actionMetrics.filter { it.label in labels }
        val hardwareResult = HardwareTestResult(
            hardware = hardware,
            apdex = Apdex().score(metrics),
            httpThroughput = AccessLogThroughput().gauge(workspace.digOutTheRawResults(cohort)),
            results = listOf(results),
            errorRate = ErrorRate().measure(metrics)
        )
        if (hardwareResult.errorRate > 0.02) {
            reportRaw("errors", listOf(hardwareResult), hardware)
            throw Exception("Error rate for $cohort is too high: ${ErrorRate().measure(metrics)}")
        }
        return hardwareResult
    }

    private fun runFreshResults(
        hardware: Hardware,
        missingResultCount: Int
    ): List<HardwareTestResult> {
        if (missingResultCount <= 0) {
            return emptyList()
        }
        logger.info("Running $missingResultCount tests to get the rest of the results for $hardware")
        val nextResultNumber = chooseNextRunNumber(hardware)
        val newRuns = nextResultNumber.until(nextResultNumber + missingResultCount)
        val workspace = hardware.isolateRuns(task).directory
        return newRuns
            .map { workspace.resolve(it.toString()) }
            .map { TestWorkspace(it) }
            .map { testHardware(hardware, it) }
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
        workspace: TestWorkspace
    ): CompletableFuture<HardwareTestResult> {
        return dataCenter(
            cohort = hardware.nameCohort(workspace),
            hardware = hardware
        ).runAsync(
            workspace,
            awsExecutor,
            virtualUsers
        ).thenApply {
            val results = postProcess(it)
            workspace.writeStatus(results)
            return@thenApply score(hardware, results, workspace)
        }
    }

    private fun dataCenter(
        cohort: String,
        hardware: Hardware
    ): ProvisioningPerformanceTest {
        val dataset: Dataset = getDataset()
        return ProvisioningPerformanceTest(
            cohort = cohort,
            infrastructureFormula = InfrastructureFormula(
                investment = investment,
                jiraFormula = DataCenterFormula(
                    apps = Apps(emptyList()),
                    application = JiraSoftwareStorage("7.13.0"),
                    jiraHomeSource = dataset.jiraHomeSource,
                    database = dataset.database,
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
                virtualUsersFormula = MulticastVirtualUsersFormula(
                    nodes = 8,
                    shadowJar = dereference("jpt.virtual-users.shadow-jar"),
                    splunkForwarder = DisabledSplunkForwarder(),
                    browser = browser
                ),
                aws = aws
            )
        )
    }

    private fun getDataset(): Dataset {
        return DatasetCatalogue()
            .custom(
                location = StorageLocation(
                    URI("s3://jpt-custom-datasets-storage-a008820-datasetbucket-1sjxdtrv5hdhj/")
                        .resolve("a12fc4c5-3973-41f0-bf56-ede393677028"),
                    Regions.EU_WEST_1
                ),
                label = "1M issues",
                databaseDownload = Duration.ofMinutes(20),
                jiraHomeDownload = Duration.ofMinutes(20)
            )
            .overrideDatabase {
                LicenseOverridingDatabase(
                    it.database,
                    listOf(
                        """
                        AAAB8w0ODAoPeNp9Uk2P2jAQvedXWOoNydmELVKLFKlL4u7SLglKQj+27cEkA3gb7GjssMu/rwnQl
                        s9DDvHMvPfmvXmTN0BGfE08n3jdftfv927J/SgnXc9/58wRQC5UXQO6j6IAqYGVwgglAxbnLB2nw
                        4w5cbOcAiaziQbUge85oZKGFybmSwjKmiMKvfjATcW1Fly6hVo64waLBdcQcQPBhot6Per5zo4lX
                        9fQjofJaMTScHj3uC+x11rgup0b3z7sudiIi+oSWQa4AhxGweD+fU6/Tb68pZ+fnh7owPO/Os8Cu
                        VujKpvCuJsfqtXMvHAE1+KKFQQGG3A+2cp412XJeQjSHLVkzVQXKOrWn/bljH/nNmslXPa30+nES
                        U4/Jikdp0k0CfNhEtNJxmwhCBGsFSWZrolZANmhECYLVQISu9gzFIb8WBhT/+zf3MyVe2DOTbWdo
                        LCd+OWSSBGpDCmFNiimjQGLLDQxihSNNmppU3Yd67c0ILksjhOxqsKU3eUsooPvG4kXUrli/MlF7
                        dayEU7kb6lepJOxOLAf7XneFmkfCuCp95nh+LdwhfegL8E5l0LzNo4IVlApi0Vy0GZvs9O6b+vHZ
                        xzBv0toB3Yuk5lCwuualHs8fSD0/3NqdZ48nBd+5bjYilfNdokZr6zmP7TmY5YwLAIUNq8MbmR8G
                        faV9ulfLz1K+3g9j1YCFDeq7aYROMQbwMIvHimNt7/bJCCIX02nj
                        """.trimIndent()
                    ))
            }
    }

    private fun coalesce(
        results: List<HardwareTestResult>,
        hardware: Hardware
    ): HardwareTestResult {
        val apdexes = results.map { it.apdex }
        val apdexSpread = apdexes.max()!! - apdexes.min()!!
        if (apdexSpread > 0.10) {
            reportRaw("comparison", results, hardware)
            throw Exception("Apdex spread for $hardware is too big: $apdexSpread. Results: $results")
        }
        val throughputUnit = Duration.ofSeconds(1)
        val averageThroughput = results
            .map { it.httpThroughput }
            .map { it.scalePeriod(throughputUnit) }
            .map { it.count }
            .average()
            .let { Throughput(it, throughputUnit) }
        return HardwareTestResult(
            hardware = hardware,
            apdex = apdexes.average(),
            httpThroughput = averageThroughput,
            results = results.flatMap { it.results },
            errorRate = results.map { it.errorRate }.max() ?: Double.NaN
        )
    }

    private fun reportRaw(
        reportName: String,
        results: List<HardwareTestResult>,
        hardware: Hardware
    ) {
        val workspace = hardware.isolateSubTask(task, reportName)
        FullReport().dump(
            results = results.flatMap { it.results },
            workspace = TestWorkspace(workspace.directory)
        )
    }

    private fun summarize() {
        val finishedResults = results.map { it.value.get() }

        val headers = arrayOf(
            "instance type",
            "node count",
            "error rate [%]",
            "apdex (0.0-1.0)",
            "throughput [HTTP requests / second]"
        )
        val format = CSVFormat.DEFAULT.withHeader(*headers).withRecordSeparator('\n')
        task.isolateReport("summary.csv").toFile().bufferedWriter().use { writer ->
            val printer = CSVPrinter(writer, format)
            finishedResults.forEach {
                printer.printRecord(
                    it.hardware.instanceType,
                    it.hardware.nodeCount,
                    it.errorRate * 100,
                    it.apdex,
                    it.httpThroughput.scalePeriod(Duration.ofSeconds(1)).count
                )
            }
        }
    }
}
