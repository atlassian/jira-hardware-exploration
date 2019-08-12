package com.atlassian.performance.tools.hardware

import com.amazonaws.services.ec2.model.InstanceType
import com.atlassian.performance.tools.aws.api.Investment
import com.atlassian.performance.tools.awsinfrastructure.api.InfrastructureFormula
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.EbsEc2Instance
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.Volume
import com.atlassian.performance.tools.awsinfrastructure.api.jira.DataCenterFormula
import com.atlassian.performance.tools.awsinfrastructure.api.jira.StandaloneFormula
import com.atlassian.performance.tools.awsinfrastructure.api.loadbalancer.ElasticLoadBalancerFormula
import com.atlassian.performance.tools.awsinfrastructure.api.virtualusers.AbsentVirtualUsersFormula
import com.atlassian.performance.tools.infrastructure.api.distribution.PublicJiraSoftwareDistribution
import com.atlassian.performance.tools.infrastructure.api.jira.JiraLaunchTimeouts
import com.atlassian.performance.tools.infrastructure.api.jira.JiraNodeConfig
import com.atlassian.performance.tools.infrastructure.api.profiler.AsyncProfiler
import com.atlassian.performance.tools.jiraperformancetests.api.CustomDatasetSourceRegistry
import com.atlassian.performance.tools.workspace.api.RootWorkspace
import org.junit.Test
import java.time.Duration

class JiraInstanceTest {
    private val aws = AtlassianAccount.DEVELOPMENT_IRELAND.aws
    private val rootWorkspace = RootWorkspace()
    private val testWorkspace = rootWorkspace.currentTask.isolateTest("JiraInstanceTest")
    val jiraVersion = "7.13.0" //""7.13.0"
    private val extraLarge = ApplicationScales().extraLarge(jiraVersion = jiraVersion, postgres = false)
    private val dataset = extraLarge.dataset.dataset

    val lifespan = Duration.ofHours(8)

    @Test
    fun shouldProvisionAnInstance() {
        provisionDcInstance()
        //provisionServerInstance()
    }

    fun provisionServerInstance() {

        val infrastructure = InfrastructureFormula(
            investment = Investment(
                useCase = "Provision an ad hoc Jira Software environment",
                lifespan = lifespan
            ),
            jiraFormula = StandaloneFormula.Builder(
                database = dataset.database,
                jiraHomeSource = dataset.jiraHomeSource,
                productDistribution = PublicJiraSoftwareDistribution(jiraVersion))
                .computer(EbsEc2Instance(InstanceType.M44xlarge))
                .jiraVolume(Volume(300))
                .databaseComputer(EbsEc2Instance(InstanceType.M44xlarge))
                .databaseVolume(Volume(300))
                .build(),
            virtualUsersFormula = AbsentVirtualUsersFormula(),
            aws = aws
        ).provision(testWorkspace.directory).infrastructure
        CustomDatasetSourceRegistry(rootWorkspace).register(infrastructure)
    }

    fun provisionDcInstance() {
        val infrastructure = InfrastructureFormula(
            investment = Investment(
                useCase = "Provision an ad hoc Jira Software environment",
                lifespan = lifespan
            ),
            jiraFormula = DataCenterFormula.Builder(
                database = dataset.database,
                jiraHomeSource = dataset.jiraHomeSource,
                productDistribution = PublicJiraSoftwareDistribution(jiraVersion))
                .loadBalancerFormula(ElasticLoadBalancerFormula())
                .computer(EbsEc2Instance(InstanceType.C59xlarge))
                .jiraVolume(Volume(300))
                .databaseComputer(EbsEc2Instance(InstanceType.M44xlarge))
                .databaseVolume(Volume(300))
                .configs((1..2).map {
                    JiraNodeConfig.Builder()
                        .name("jira-node-$it")
                        .profiler(AsyncProfiler())
                        .launchTimeouts(
                            JiraLaunchTimeouts.Builder()
                                .initTimeout(Duration.ofMinutes(7))
                                .build()

                        ).build()
                })
                .build(),
            virtualUsersFormula = AbsentVirtualUsersFormula(),
            aws = aws
        ).provision(testWorkspace.directory).infrastructure
        CustomDatasetSourceRegistry(rootWorkspace).register(infrastructure)
    }

}
