package com.atlassian.performance.tools.hardware

import com.amazonaws.services.ec2.model.InstanceType
import com.atlassian.performance.tools.aws.api.Investment
import com.atlassian.performance.tools.awsinfrastructure.api.InfrastructureFormula
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.EbsEc2Instance
import com.atlassian.performance.tools.awsinfrastructure.api.jira.DataCenterFormula
import com.atlassian.performance.tools.awsinfrastructure.api.jira.StandaloneFormula
import com.atlassian.performance.tools.awsinfrastructure.api.virtualusers.AbsentVirtualUsersFormula
import com.atlassian.performance.tools.awsinfrastructure.api.virtualusers.MulticastVirtualUsersFormula
import com.atlassian.performance.tools.infrastructure.api.browser.chromium.Chromium69
import com.atlassian.performance.tools.infrastructure.api.distribution.PublicJiraSoftwareDistribution
import com.atlassian.performance.tools.infrastructure.api.jira.JiraJvmArgs
import com.atlassian.performance.tools.infrastructure.api.jira.JiraLaunchTimeouts
import com.atlassian.performance.tools.infrastructure.api.jira.JiraNodeConfig
import com.atlassian.performance.tools.infrastructure.api.jvm.JvmArg
import com.atlassian.performance.tools.infrastructure.api.profiler.AsyncProfiler
import com.atlassian.performance.tools.io.api.dereference
import com.atlassian.performance.tools.jiraperformancetests.api.CustomDatasetSourceRegistry
import com.atlassian.performance.tools.workspace.api.RootWorkspace
import org.junit.Test
import java.time.Duration

class JiraInstanceTest {
    private val aws = IntegrationTestRuntime.aws
    private val rootWorkspace = RootWorkspace()
    private val testWorkspace = rootWorkspace.currentTask.isolateTest("JiraInstanceTest")
    private val extraLarge = extraLarge(false)
    private val dataset = extraLarge.dataset.dataset

    val adminPwd = "admin"//"MasterPassword18"
    val jiraVersion = "7.13.0" //""7.13.0"
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
                .computer(EbsEc2Instance(InstanceType.M44xlarge).withVolumeSize(300))
                .databaseComputer(EbsEc2Instance(InstanceType.M44xlarge).withVolumeSize(300))
                .adminPwd(adminPwd)
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
                .computer(EbsEc2Instance(InstanceType.C518xlarge).withVolumeSize(300))
                .databaseComputer(EbsEc2Instance(InstanceType.M44xlarge).withVolumeSize(300))
                .configs((1..3).map {
                    JiraNodeConfig.Builder()
                        .name("jira-node-$it")
                        .profiler(AsyncProfiler())
                        .jvmArgs(JiraJvmArgs("56G", "56G",
                            listOf(
                                JvmArg("-XX:+UseG1GC"))))
                        .launchTimeouts(
                            JiraLaunchTimeouts.Builder()
                                .initTimeout(Duration.ofMinutes(7))
                                .offlineTimeout(Duration.ofMinutes(15))
                                .build()

                        ).build()
                })
                .adminPwd(adminPwd)
                .build(),
//            virtualUsersFormula = MulticastVirtualUsersFormula.Builder(
//                nodes = 12,
//                shadowJar = dereference("jpt.virtual-users.shadow-jar")
//            )
//                .browser(Chromium69())
//                .build(),
            virtualUsersFormula = AbsentVirtualUsersFormula(),
            aws = aws
        ).provision(testWorkspace.directory).infrastructure
        CustomDatasetSourceRegistry(rootWorkspace).register(infrastructure)
    }

}
