package com.atlassian.performance.tools.hardware

import com.amazonaws.services.ec2.model.InstanceType.C54xlarge
import com.amazonaws.services.ec2.model.InstanceType.M44xlarge
import com.atlassian.performance.tools.aws.api.Investment
import com.atlassian.performance.tools.awsinfrastructure.api.InfrastructureFormula
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.EbsEc2Instance
import com.atlassian.performance.tools.awsinfrastructure.api.jira.DataCenterFormula
import com.atlassian.performance.tools.awsinfrastructure.api.loadbalancer.ElasticLoadBalancerFormula
import com.atlassian.performance.tools.awsinfrastructure.api.virtualusers.MulticastVirtualUsersFormula
import com.atlassian.performance.tools.hardware.tuning.HeapTuning
import com.atlassian.performance.tools.infrastructure.api.distribution.PublicJiraSoftwareDistribution
import com.atlassian.performance.tools.infrastructure.api.jira.JiraNodeConfig
import com.atlassian.performance.tools.infrastructure.api.profiler.AsyncProfiler
import com.atlassian.performance.tools.io.api.dereference
import com.atlassian.performance.tools.jiraperformancetests.api.ProvisioningPerformanceTest
import com.atlassian.performance.tools.jirasoftwareactions.api.actions.ViewBacklogAction
import com.atlassian.performance.tools.lib.ErrorGauge
import com.atlassian.performance.tools.lib.LogConfigurationFactory
import com.atlassian.performance.tools.lib.Ratio
import com.atlassian.performance.tools.lib.infrastructure.BestEffortProfiler
import com.atlassian.performance.tools.lib.infrastructure.PatientChromium69
import com.atlassian.performance.tools.lib.infrastructure.WgetOracleJdk
import com.atlassian.performance.tools.report.api.StandardTimeline
import org.apache.logging.log4j.core.config.ConfigurationFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import java.time.Duration

class BacklogPageIT {

    private val workspace = IntegrationTestRuntime.rootWorkspace.currentTask
    private val jswVersion = "7.13.0"

    @Before
    fun setUp() {
        ConfigurationFactory.setConfigurationFactory(LogConfigurationFactory(workspace))
    }

    @Test
    fun shouldNotTimeOut() {
        val scale = ApplicationScales().extraLarge(jiraVersion = jswVersion, postgres = false)
        val test = ProvisioningPerformanceTest(
            cohort = "backlog-test",
            infrastructureFormula = InfrastructureFormula(
                investment = Investment(
                    useCase = "Reproduce the backlog timeout",
                    lifespan = Duration.ofHours(2)
                ),
                jiraFormula = jira(scale),
                virtualUsersFormula = MulticastVirtualUsersFormula.Builder(
                    nodes = scale.vuNodes,
                    shadowJar = dereference("jpt.virtual-users.shadow-jar")
                )
                    .browser(PatientChromium69())
                    .build(),
                aws = IntegrationTestRuntime.prepareAws()
            )
        )

        val result = test.execute(
            workspace.isolateTest("BacklogPageIT"),
            HardwareExploration.ScaleVirtualUserOptions(scale)
        ).prepareForJudgement(StandardTimeline(scale.load.total))
        val actionError = ErrorGauge()
            .measureActions(result.actionMetrics)
            .single { it.actionLabel == ViewBacklogAction.VIEW_BACKLOG.label }

        assertThat(actionError.ratio).isLessThan(Ratio(0.01))
    }

    private fun jira(scale: ApplicationScale): DataCenterFormula {
        val hardware = Hardware(
            jira = C54xlarge,
            nodeCount = 4,
            db = M44xlarge
        )
        return DataCenterFormula.Builder(
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
                    .let { HeapTuning(50).tune(it, hardware, scale) }
            })
            .loadBalancerFormula(ElasticLoadBalancerFormula())
            .computer(EbsEc2Instance(hardware.jira).withVolumeSize(300))
            .databaseComputer(EbsEc2Instance(hardware.db).withVolumeSize(300))
            .adminUser(scale.dataset.adminLogin)
            .adminPwd(scale.dataset.adminPassword)
            .build()
    }
}
