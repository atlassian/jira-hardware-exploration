package com.atlassian.performance.tools

import com.amazonaws.services.ec2.model.InstanceType
import com.atlassian.performance.tools.aws.api.Investment
import com.atlassian.performance.tools.awsinfrastructure.api.InfrastructureFormula
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.EbsEc2Instance
import com.atlassian.performance.tools.awsinfrastructure.api.jira.DataCenterFormula
import com.atlassian.performance.tools.awsinfrastructure.api.loadbalancer.ElasticLoadBalancerFormula
import com.atlassian.performance.tools.awsinfrastructure.api.virtualusers.AbsentVirtualUsersFormula
import com.atlassian.performance.tools.hardware.ApplicationScales
import com.atlassian.performance.tools.hardware.Hardware
import com.atlassian.performance.tools.hardware.IntegrationTestRuntime
import com.atlassian.performance.tools.hardware.tuning.HeapTuning
import com.atlassian.performance.tools.infrastructure.api.distribution.PublicJiraSoftwareDistribution
import com.atlassian.performance.tools.infrastructure.api.jira.JiraNodeConfig
import com.atlassian.performance.tools.lib.LogConfigurationFactory
import com.atlassian.performance.tools.lib.infrastructure.WgetOracleJdk
import org.apache.logging.log4j.core.config.ConfigurationFactory
import org.junit.Test
import java.time.Duration

class JswVersionIT {

    private val workspace = IntegrationTestRuntime.rootWorkspace.currentTask

    @Test
    fun shouldReproTheBug() {
        ConfigurationFactory.setConfigurationFactory(LogConfigurationFactory(workspace))
        val jswVersion = "7.13.0"
        val hardware = Hardware(
            jira = InstanceType.C59xlarge,
            nodeCount = 1,
            db = InstanceType.M44xlarge
        )
        val scale = ApplicationScales().extraLarge(jiraVersion = jswVersion, postgres = false)
        val aws = IntegrationTestRuntime.prepareAws()
        val infrastructure = InfrastructureFormula(
            investment = Investment(
                useCase = "Repro the JSW version bug",
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
                        .jdk(WgetOracleJdk())
                        .build()
                        .let { HeapTuning().tune(it, hardware, scale) }
                })
                .loadBalancerFormula(ElasticLoadBalancerFormula())
                .computer(EbsEc2Instance(hardware.jira).withVolumeSize(300))
                .databaseComputer(EbsEc2Instance(hardware.db).withVolumeSize(300))
                .build(),
            virtualUsersFormula = AbsentVirtualUsersFormula(),
            aws = aws
        ).provision(
            workspace.isolateTest("JswVersionRepro").directory
        ).infrastructure
        val versionsPage = infrastructure.jira.address.resolve("plugins/servlet/applications/versions-licenses")
        println("Check $versionsPage")
    }
}
