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
import com.atlassian.performance.tools.hardware.tuning.HeapTuning
import com.atlassian.performance.tools.infrastructure.api.distribution.PublicJiraSoftwareDistribution
import com.atlassian.performance.tools.infrastructure.api.jira.JiraNodeConfig
import com.atlassian.performance.tools.infrastructure.api.profiler.AsyncProfiler
import com.atlassian.performance.tools.io.api.dereference
import com.atlassian.performance.tools.jiraperformancetests.api.ProvisioningPerformanceTest
import com.atlassian.performance.tools.lib.infrastructure.BestEffortProfiler
import com.atlassian.performance.tools.lib.infrastructure.PatientChromium69
import com.atlassian.performance.tools.lib.infrastructure.WgetOracleJdk
import com.atlassian.performance.tools.report.api.FullReport
import com.atlassian.performance.tools.report.api.FullTimeline
import com.atlassian.performance.tools.workspace.api.TestWorkspace
import org.junit.Test
import java.time.Duration

class BrowseBoardsIT {

    @Test
    fun shouldReproTheBug() {
        val jswVersion = "7.13.0"
        val hardware = Hardware(
            jira = InstanceType.C59xlarge,
            nodeCount = 3,
            db = InstanceType.M44xlarge
        )
        val tuning = HeapTuning()
        val scale = ApplicationScales().extraLarge(jiraVersion = jswVersion, postgres = false)
        val test = ProvisioningPerformanceTest(
            cohort = "repro",
            infrastructureFormula = InfrastructureFormula(
                investment = Investment(
                    useCase = "Repro the Browse Boards bug",
                    lifespan = Duration.ofHours(2)
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
                aws = IntegrationTestRuntime.aws
            )
        )
        val workspace = IntegrationTestRuntime.rootWorkspace.currentTask.isolateTest("BrowseBoardsRepro")
        val vuOptions = HardwareExploration.ScaleVirtualUserOptions(scale)
        val rawResult = test.execute(workspace, vuOptions)
        val result = rawResult.prepareForJudgement(FullTimeline())
        FullReport().dump(
            results = listOf(result),
            workspace = TestWorkspace(workspace.directory)
        )
    }
}
