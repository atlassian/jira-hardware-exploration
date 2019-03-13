package com.atlassian.performance.tools.hardware

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain
import com.amazonaws.regions.Regions.EU_WEST_1
import com.amazonaws.services.ec2.model.InstanceType.C48xlarge
import com.atlassian.performance.tools.aws.api.Aws
import com.atlassian.performance.tools.aws.api.Investment
import com.atlassian.performance.tools.aws.api.TextCapacityMediator
import com.atlassian.performance.tools.lib.LogConfigurationFactory
import com.atlassian.performance.tools.virtualusers.api.TemporalRate
import com.atlassian.performance.tools.virtualusers.api.VirtualUserLoad
import com.atlassian.performance.tools.workspace.api.RootWorkspace
import com.atlassian.performance.tools.workspace.api.TestWorkspace
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.apache.logging.log4j.core.config.ConfigurationFactory
import org.junit.BeforeClass
import org.junit.Test
import java.nio.file.Paths
import java.time.Duration

class HardwareExplorationIT {

    private val logger: Logger = LogManager.getLogger(this::class.java)

    private val failureTolerance = object : FailureTolerance {
        val cleaning = CleaningFailureTolerance()
        val logging = LoggingFailureTolerance(logger)

        override fun handle(failure: Exception, workspace: TestWorkspace) {
            when {
                causedByJperf387(failure) -> cleanAfterKnownIssue(failure, workspace, "JPERF-387")
                causedByJperf382(failure) -> cleanAfterKnownIssue(failure, workspace, "JPERF-382")
                else -> logging.handle(failure, workspace)
            }
        }

        private fun causedByJperf382(
            failure: Exception
        ): Boolean = failure
            .message!!
            .contains("java.net.SocketTimeoutException: Read timed out")

        private fun causedByJperf387(
            failure: Exception
        ): Boolean = failure
            .message!!
            .contains("Failed to install")

        private fun cleanAfterKnownIssue(
            failure: Exception,
            workspace: TestWorkspace,
            issueKey: String
        ) {
            logger.warn("Failure in $workspace due to https://ecosystem.atlassian.net/browse/$issueKey, cleaning...")
            cleaning.handle(failure, workspace)
        }
    }

    @Test
    fun shouldExploreHardware() {
        HardwareExploration(
            scale = ApplicationScale(
                description = "Jira L profile",
                dataset = MyDatasetCatalogue().oneMillionIssues(),
                load = VirtualUserLoad.Builder()
                    .virtualUsers(75)
                    .ramp(Duration.ofSeconds(90))
                    .flat(Duration.ofMinutes(5))
                    .maxOverallLoad(TemporalRate(15.0, Duration.ofSeconds(1)))
                    .build(),
                vuNodes = 6
            ),
            guidance = ExplorationGuidance(
                instanceTypes = listOf(
                    C48xlarge
                ),
                maxNodeCount = 1,
                minNodeCountForAvailability = 1,
                repeats = 1,
                minApdexGain = 0.01,
                maxApdexSpread = 0.10,
                maxErrorRate = 0.05,
                pastFailures = failureTolerance
            ),
            investment = Investment(
                useCase = "Test hardware recommendations - $taskName",
                lifespan = Duration.ofHours(2)
            ),
            aws = Aws(
                credentialsProvider = DefaultAWSCredentialsProviderChain(),
                region = EU_WEST_1,
                regionsWithHousekeeping = listOf(EU_WEST_1),
                capacity = TextCapacityMediator(EU_WEST_1),
                batchingCloudformationRefreshPeriod = Duration.ofSeconds(20)
            ),
            task = workspace
        ).exploreHardware()
    }

    companion object {
        const val taskName = "QUICK-94-multiusers-repro"
        private val workspace = RootWorkspace(Paths.get("build")).isolateTask(taskName)

        @BeforeClass
        @JvmStatic
        fun setUp() {
            ConfigurationFactory.setConfigurationFactory(LogConfigurationFactory(workspace))
        }
    }
}
