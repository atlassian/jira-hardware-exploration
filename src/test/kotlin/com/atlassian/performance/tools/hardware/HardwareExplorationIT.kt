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
import com.atlassian.performance.tools.hardware.tuning.HeapTuning
import com.atlassian.performance.tools.infrastructure.api.distribution.PublicJiraSoftwareDistribution
import com.atlassian.performance.tools.jvmtasks.api.TaskTimer.time
import com.atlassian.performance.tools.lib.s3cache.S3Cache
import com.atlassian.performance.tools.lib.workspace.GitRepo2
import com.atlassian.performance.tools.virtualusers.api.TemporalRate
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
        C59xlarge,
        C518xlarge
    )
    private val resultCache = HardwareExplorationResultCache(workspace.directory.resolve("processed-cache.json"))
    private val s3Cache = S3Cache(
        transfer = TransferManagerBuilder.standard()
            .withS3Client(aws.s3)
            .build(),
        bucketName = "quicksilver-jhwr-cache-ireland",
        cacheKey = taskName,
        localPath = workspace.directory
    )

    @Test
    fun shouldExploreHardware() {
        requireCleanRepo()
        logger.info("Using $s3Cache")
        time("download") { s3Cache.download() }
        val jiraExploration = try {
            exploreJiraHardware()
        } finally {
            time("upload") { s3Cache.upload() }
        }
        val jiraRecommendations = recommendJiraHardware(jiraExploration)
        try {
            exploreDbHardware(jiraRecommendations, jiraExploration)
        } finally {
            time("upload") { s3Cache.upload() }
        }
    }

    private fun requireCleanRepo() {
        val status = Git(GitRepo2.findInAncestors(File(".").absoluteFile)).status().call()
        if (status.isClean.not()) {
            throw Exception("Your Git repo is not clean. Please commit the changes and consider pushing them.")
        }
    }

    private fun recommendJiraHardware(
        jiraExploration: List<HardwareExplorationResult>
    ): List<HardwareTestResult> {
        val candidates = jiraExploration
            .mapNotNull { it.testResult }
            .filter { it.apdex > 0.70 }
            .filter { it.errorRate < 0.01 }
        return listOf(
            recommendByApdex(candidates),
            recommendByCostEffectiveness(candidates)
        )
    }

    private fun recommendByApdex(
        candidates: List<HardwareTestResult>
    ): HardwareTestResult {
        val highestApdex = candidates
            .sortedByDescending { it.apdex }
            .firstOrNull()
            ?: throw Exception("We don't have an Apdex recommendation")
        logger.info("Recommended the highest Apdex: $highestApdex")
        return highestApdex
    }

    private fun recommendByCostEffectiveness(
        candidates: List<HardwareTestResult>
    ): HardwareTestResult {
        val mostCostEfficient = candidates
            .sortedByDescending { it.apdexPerUsdUpkeep }
            .firstOrNull()
            ?: throw Exception("We don't have a cost-effectiveness recommendation")
        logger.info("Recommended the most cost-efficient: $mostCostEfficient")
        return mostCostEfficient
    }

    private fun exploreJiraHardware(): List<HardwareExplorationResult> = explore(
        JiraExplorationGuidance(
            instanceTypes = jiraInstanceTypes,
            maxNodeCount = 16,
            minNodeCountForAvailability = 3,
            minApdexGain = 0.01,
            minThroughputGain = TemporalRate(5.0, Duration.ofSeconds(1)),
            db = M44xlarge,
            resultsCache = resultCache
        )
    )

    private fun explore(
        guidance: ExplorationGuidance
    ): List<HardwareExplorationResult> = HardwareExploration(
        product = PublicJiraSoftwareDistribution("7.13.0"),
        scale = extraLarge(jira8 = false, postgres = false),
        guidance = guidance,
        apdexSpreadWarningThreshold = 0.10,
        errorRateWarningThreshold = 0.05,
        pastFailures = BugAwareTolerance(logger),
        repeats = 2,
        investment = Investment(
            useCase = "Test hardware recommendations - $taskName",
            lifespan = Duration.ofHours(2)
        ),
        tuning = HeapTuning(),
        cache = s3Cache,
        aws = aws,
        task = workspace
    ).exploreHardware()

    private fun exploreDbHardware(
        jiraRecommendations: List<HardwareTestResult>,
        jiraExploration: List<HardwareExplorationResult>
    ): List<HardwareExplorationResult> = explore(
        DbExplorationGuidance(
            dbs = listOf(
                M44xlarge
            ),
            jiraRecommendations = jiraRecommendations,
            jiraExploration = jiraExploration,
            jiraOrder = jiraInstanceTypes,
            resultsCache = resultCache
        )
    )
}
