package com.atlassian.performance.tools.hardware

import com.amazonaws.services.ec2.model.InstanceType.*
import com.amazonaws.services.s3.transfer.TransferManagerBuilder
import com.atlassian.performance.tools.aws.api.Investment
import com.atlassian.performance.tools.hardware.IntegrationTestRuntime.aws
import com.atlassian.performance.tools.hardware.IntegrationTestRuntime.logContext
import com.atlassian.performance.tools.hardware.IntegrationTestRuntime.taskName
import com.atlassian.performance.tools.hardware.IntegrationTestRuntime.workspace
import com.atlassian.performance.tools.hardware.failure.BugAwareTolerance
import com.atlassian.performance.tools.hardware.guidance.DbExplorationGuidance
import com.atlassian.performance.tools.hardware.guidance.ExplorationGuidance
import com.atlassian.performance.tools.hardware.guidance.JiraExplorationGuidance
import com.atlassian.performance.tools.infrastructure.api.distribution.PublicJiraSoftwareDistribution
import com.atlassian.performance.tools.jvmtasks.api.TaskTimer.time
import com.atlassian.performance.tools.lib.s3cache.S3Cache
import com.atlassian.performance.tools.lib.workspace.GitRepo2
import org.apache.logging.log4j.Logger
import org.eclipse.jgit.api.Git
import org.junit.Test
import java.io.File
import java.time.Duration

class HardwareExplorationIT {

    private val logger: Logger = logContext.getLogger(this::class.java.canonicalName)

    private val jiraInstanceTypes = listOf(
        C52xlarge,
        C54xlarge,
        C48xlarge,
        C518xlarge
    )
    private val resultCache = HardwareExplorationResultCache(workspace.directory.resolve("processed-cache.json"))

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
    ): List<HardwareTestResult> {
        val recommendations = jiraExploration
            .mapNotNull { it.testResult }
            .filter { it.apdex > 0.70 }
            .sortedByDescending { it.apdex }
            .take(2)
        logger.info("Recommending $recommendations")
        return recommendations
    }

    private fun exploreJiraHardware(): List<HardwareExplorationResult> = explore(
        JiraExplorationGuidance(
            instanceTypes = jiraInstanceTypes,
            maxNodeCount = 16,
            minNodeCountForAvailability = 3,
            minApdexGain = 0.01,
            db = M44xlarge,
            resultsCache = resultCache
        )
    )

    private fun explore(
        guidance: ExplorationGuidance
    ): List<HardwareExplorationResult> = HardwareExploration(
        product = PublicJiraSoftwareDistribution("8.1.0"),
        scale = extraLarge(jira8 = true),
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
        jiraRecommendations: List<HardwareTestResult>,
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
