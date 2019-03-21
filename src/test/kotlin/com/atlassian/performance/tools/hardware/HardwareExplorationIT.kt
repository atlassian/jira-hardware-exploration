package com.atlassian.performance.tools.hardware

import com.amazonaws.regions.Regions.EU_WEST_1
import com.amazonaws.services.ec2.model.InstanceType.*
import com.amazonaws.services.s3.transfer.TransferManagerBuilder
import com.atlassian.performance.tools.aws.api.Investment
import com.atlassian.performance.tools.aws.api.StorageLocation
import com.atlassian.performance.tools.awsinfrastructure.api.DatasetCatalogue
import com.atlassian.performance.tools.hardware.IntegrationTestRuntime.aws
import com.atlassian.performance.tools.hardware.IntegrationTestRuntime.logContext
import com.atlassian.performance.tools.hardware.IntegrationTestRuntime.taskName
import com.atlassian.performance.tools.hardware.IntegrationTestRuntime.workspace
import com.atlassian.performance.tools.hardware.failure.BugAwareTolerance
import com.atlassian.performance.tools.hardware.guidance.DbExplorationGuidance
import com.atlassian.performance.tools.hardware.guidance.ExplorationGuidance
import com.atlassian.performance.tools.hardware.guidance.JiraExplorationGuidance
import com.atlassian.performance.tools.jvmtasks.api.TaskTimer.time
import com.atlassian.performance.tools.lib.LicenseOverridingDatabase
import com.atlassian.performance.tools.lib.overrideDatabase
import com.atlassian.performance.tools.lib.s3cache.S3Cache
import com.atlassian.performance.tools.lib.toExistingFile
import com.atlassian.performance.tools.lib.workspace.GitRepo2
import com.atlassian.performance.tools.virtualusers.api.TemporalRate
import com.atlassian.performance.tools.virtualusers.api.VirtualUserLoad
import org.apache.logging.log4j.Logger
import org.eclipse.jgit.api.Git
import org.junit.Test
import java.io.File
import java.net.URI
import java.nio.file.Paths
import java.time.Duration

class HardwareExplorationIT {

    private val logger: Logger = logContext.getLogger(this::class.java.canonicalName)
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
                    ?: throw Exception("Put a Jira license to ${localLicense.toAbsolutePath()}")
            ))
    }
    private val jiraInstanceTypes = listOf(
        C52xlarge,
        C54xlarge,
        C48xlarge,
        C518xlarge
    )
    private val resultCache = HardwareExplorationResultCache(workspace.directory.resolve("result-cache.json"))

    @Test
    fun shouldExploreHardware() {
        requireCleanRepo()
        val cache = getWorkspaceCache()
        logger.info("Using $cache")
        time("download") { cache.download() }
        val jiraExploration = try {
            exploreJiraHardware()
        } finally {
            time("upload") { cache.upload() }
        }
        val jiraRecommendations = recommendJiraHardware(jiraExploration)
        try {
            exploreDbHardware(jiraRecommendations, jiraExploration)
        } finally {
            time("upload") { cache.upload() }
        }
    }

    private fun requireCleanRepo() {
        val status = Git(GitRepo2.findInAncestors(File(".").absoluteFile)).status().call()
        if (status.isClean.not()) {
            throw Exception("Your Git repo is not clean. Please commit the changes and consider pushing them.")
        }
    }

    private fun getWorkspaceCache(): S3Cache = S3Cache(
        transfer = TransferManagerBuilder.standard()
            .withS3Client(aws.s3)
            .build(),
        bucketName = "quicksilver-jhwr-cache-ireland",
        cacheKey = taskName,
        localPath = workspace.directory
    )

    private fun recommendJiraHardware(
        jiraExploration: List<HardwareExplorationResult>
    ): List<Hardware> {
        val handPickedHardware = listOf(
            Hardware(C48xlarge, 2, M4Xlarge),
            Hardware(C48xlarge, 3, M4Xlarge)
        )
        logger.warn("Hand-picking hardware: $handPickedHardware")
        val recommendations = jiraExploration.filter {
            it.testResult?.hardware in handPickedHardware
        }
        logger.info("Recommending $recommendations")
        return handPickedHardware
    }

    private fun exploreJiraHardware(): List<HardwareExplorationResult> = explore(
        JiraExplorationGuidance(
            instanceTypes = jiraInstanceTypes,
            maxNodeCount = 16,
            minNodeCountForAvailability = 3,
            minApdexGain = 0.01,
            db = M4Xlarge,
            resultsCache = resultCache
        )
    )

    private fun explore(
        guidance: ExplorationGuidance
    ): List<HardwareExplorationResult> = HardwareExploration(
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
        guidance = guidance,
        maxApdexSpread = 0.10,
        maxErrorRate = 0.05,
        pastFailures = BugAwareTolerance(logger),
        repeats = 2,
        investment = Investment(
            useCase = "Test hardware recommendations - $taskName",
            lifespan = Duration.ofHours(2)
        ),
        aws = aws,
        task = workspace
    ).exploreHardware()

    private fun exploreDbHardware(
        jiraRecommendations: List<Hardware>,
        jiraExploration: List<HardwareExplorationResult>
    ): List<HardwareExplorationResult> = explore(
        DbExplorationGuidance(
            dbs = listOf(
                M4Large,
                M4Xlarge,
                M42xlarge,
                M44xlarge
            ),
            jiraRecommendations = jiraRecommendations,
            jiraExploration = jiraExploration,
            jiraOrder = jiraInstanceTypes,
            resultsCache = resultCache
        )
    )
}
