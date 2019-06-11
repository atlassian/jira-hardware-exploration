package com.atlassian.performance.tools

import com.amazonaws.services.ec2.model.InstanceType
import com.atlassian.performance.tools.aws.api.Investment
import com.atlassian.performance.tools.awsinfrastructure.api.InfrastructureFormula
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.EbsEc2Instance
import com.atlassian.performance.tools.awsinfrastructure.api.jira.DataCenterFormula
import com.atlassian.performance.tools.awsinfrastructure.api.loadbalancer.ElasticLoadBalancerFormula
import com.atlassian.performance.tools.awsinfrastructure.api.virtualusers.MulticastVirtualUsersFormula
import com.atlassian.performance.tools.hardware.ApplicationScales
import com.atlassian.performance.tools.hardware.Hardware
import com.atlassian.performance.tools.hardware.HardwareExploration
import com.atlassian.performance.tools.hardware.IntegrationTestRuntime
import com.atlassian.performance.tools.hardware.IntegrationTestRuntime.rootWorkspace
import com.atlassian.performance.tools.hardware.tuning.HeapTuning
import com.atlassian.performance.tools.infrastructure.api.distribution.PublicJiraSoftwareDistribution
import com.atlassian.performance.tools.infrastructure.api.jira.JiraNodeConfig
import com.atlassian.performance.tools.infrastructure.api.profiler.AsyncProfiler
import com.atlassian.performance.tools.io.api.dereference
import com.atlassian.performance.tools.lib.LogConfigurationFactory
import com.atlassian.performance.tools.lib.awsinfrastructure.CacheableInfrastructure
import com.atlassian.performance.tools.lib.infrastructure.BestEffortProfiler
import com.atlassian.performance.tools.lib.infrastructure.PatientChromium69
import com.atlassian.performance.tools.lib.infrastructure.WgetOracleJdk
import com.atlassian.performance.tools.report.api.FullReport
import com.atlassian.performance.tools.report.api.FullTimeline
import com.atlassian.performance.tools.report.api.result.RawCohortResult
import com.atlassian.performance.tools.workspace.api.TestWorkspace
import org.apache.logging.log4j.core.config.ConfigurationFactory
import org.junit.Test
import java.time.Duration

class BrowseBoardsIT {

    private val workspace = IntegrationTestRuntime.rootWorkspace.currentTask

    @Test
    fun shouldReproTheBug() {
        val testWorkspace = workspace.isolateTest("BrowseBoardsRepro")
        ConfigurationFactory.setConfigurationFactory(LogConfigurationFactory(workspace))
        val jswVersion = "7.13.0"
        val hardware = Hardware(
            jira = InstanceType.C59xlarge,
            nodeCount = 3,
            db = InstanceType.M44xlarge
        )
        val tuning = HeapTuning()
        val scale = ApplicationScales().extraLarge(jiraVersion = jswVersion, postgres = false)
        val aws = IntegrationTestRuntime.prepareAws()
        val infrastructure = CacheableInfrastructure(
            formula = InfrastructureFormula(
                investment = Investment(
                    useCase = "Repro the Browse Boards bug",
                    lifespan = Duration.ofHours(8)
                ),
                jiraFormula = DataCenterFormula.Builder(
                    productDistribution = PublicJiraSoftwareDistribution(jswVersion),
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
            ),
            cache = rootWorkspace
                .isolateTask("Cache infra")
                .isolateReport("infra-for-browse-boards.json"),
            workspace = workspace.directory,
            aws = aws
        ).obtain()
        val vuOptions = HardwareExploration.ScaleVirtualUserOptions(scale)
        infrastructure.applyLoad(vuOptions)
        val rawResult = RawCohortResult.Factory().fullResult(
            cohort = "repro",
            results = infrastructure.downloadResults(workspace.directory)
        )
        val result = rawResult.prepareForJudgement(FullTimeline())
        FullReport().dump(
            results = listOf(result),
            workspace = TestWorkspace(testWorkspace.directory)
        )
    }
}
