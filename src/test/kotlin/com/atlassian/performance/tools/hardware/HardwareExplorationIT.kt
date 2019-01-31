package com.atlassian.performance.tools.hardware

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain
import com.amazonaws.regions.Regions.EU_WEST_1
import com.amazonaws.services.ec2.model.InstanceType.*
import com.atlassian.performance.tools.aws.api.Aws
import com.atlassian.performance.tools.aws.api.Investment
import com.atlassian.performance.tools.aws.api.StorageLocation
import com.atlassian.performance.tools.aws.api.TextCapacityMediator
import com.atlassian.performance.tools.awsinfrastructure.api.DatasetCatalogue
import com.atlassian.performance.tools.lib.LicenseOverridingDatabase
import com.atlassian.performance.tools.lib.LogConfigurationFactory
import com.atlassian.performance.tools.lib.overrideDatabase
import com.atlassian.performance.tools.lib.toExistingFile
import com.atlassian.performance.tools.virtualusers.api.VirtualUserLoad
import com.atlassian.performance.tools.workspace.api.RootWorkspace
import org.apache.logging.log4j.core.config.ConfigurationFactory
import org.junit.BeforeClass
import org.junit.Test
import java.net.URI
import java.nio.file.Paths
import java.time.Duration

class HardwareExplorationIT {

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


    @Test
    fun shouldExploreHardware() {
        HardwareExploration(
            scale = ApplicationScale(
                description = "Jira L profile",
                dataset = oneMillionIssues,
                load = VirtualUserLoad(
                    virtualUsers = 200,
                    ramp = Duration.ofSeconds(30),
                    flat = Duration.ofMinutes(20)
                )
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
                maxErrorRate = 0.05
            ),
            investment = Investment(
                useCase = "Test hardware recommendations",
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
        private val workspace = RootWorkspace(Paths.get("build")).isolateTask("QUICK-53")

        @BeforeClass
        @JvmStatic
        fun setUp() {
            ConfigurationFactory.setConfigurationFactory(LogConfigurationFactory(workspace))
        }
    }
}
