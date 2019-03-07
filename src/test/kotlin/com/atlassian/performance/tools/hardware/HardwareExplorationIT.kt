package com.atlassian.performance.tools.hardware

import com.amazonaws.regions.Regions.EU_WEST_1
import com.amazonaws.services.ec2.model.InstanceType.*
import com.atlassian.performance.tools.aws.api.Investment
import com.atlassian.performance.tools.aws.api.StorageLocation
import com.atlassian.performance.tools.awsinfrastructure.api.DatasetCatalogue
import com.atlassian.performance.tools.hardware.IntegrationTestRuntime.taskName
import com.atlassian.performance.tools.lib.LicenseOverridingDatabase
import com.atlassian.performance.tools.lib.overrideDatabase
import com.atlassian.performance.tools.lib.toExistingFile
import com.atlassian.performance.tools.virtualusers.api.TemporalRate
import com.atlassian.performance.tools.virtualusers.api.VirtualUserLoad
import com.atlassian.performance.tools.workspace.api.TestWorkspace
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.junit.Test
import java.net.URI
import java.nio.file.Paths
import java.time.Duration

class HardwareExplorationIT {

    private val logger: Logger = LogManager.getLogger(this::class.java)
    private val oneMillionIssues = DatasetCatalogue().custom(
        location = StorageLocation(
            uri = URI("s3://jpt-custom-datasets-storage-a008820-datasetbucket-1sjxdtrv5hdhj/")
                .resolve("a12fc4c5-3973-41f0-bf56-ede393677028"),
            region = EU_WEST_1
        ),
        label = "1M issues",
        databaseDownload = Duration.ofMinutes(20),
        jiraHomeDownload = Duration.ofMinutes(20)
    ).overrideDatabase { originalDataset ->
        val localLicense = Paths.get("jira-license.txt")
        LicenseOverridingDatabase(
            originalDataset.database,
            listOf(
                localLicense
                    .toExistingFile()
                    ?.readText()
                    ?: throw  Exception("Put a Jira license to ${localLicense.toAbsolutePath()}")
            ))
    }


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
                dataset = oneMillionIssues,
                load = VirtualUserLoad.Builder()
                    .virtualUsers(75)
                    .ramp(Duration.ofSeconds(90))
                    .flat(Duration.ofMinutes(20))
                    .maxOverallLoad(TemporalRate(15.0, Duration.ofSeconds(1)))
                    .build(),
                vuNodes = 6
            ),
            guidance = ExplorationGuidance(
                instanceTypes = listOf(
                    C52xlarge,
                    C54xlarge,
                    C48xlarge,
                    C518xlarge
                ),
                maxNodeCount = 16,
                minNodeCountForAvailability = 3,
                repeats = 2,
                minApdexGain = 0.01,
                maxApdexSpread = 0.10,
                maxErrorRate = 0.05,
                pastFailures = failureTolerance
            ),
            investment = Investment(
                useCase = "Test hardware recommendations - $taskName",
                lifespan = Duration.ofHours(2)
            ),
            aws = IntegrationTestRuntime.aws,
            task = IntegrationTestRuntime.workspace
        ).exploreHardware()
    }
}
