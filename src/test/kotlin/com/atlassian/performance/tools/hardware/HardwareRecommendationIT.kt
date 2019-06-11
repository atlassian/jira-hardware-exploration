package com.atlassian.performance.tools.hardware

import com.amazonaws.services.ec2.model.InstanceType.*
import com.amazonaws.services.s3.transfer.TransferManagerBuilder
import com.atlassian.performance.tools.hardware.IntegrationTestRuntime.prepareAws
import com.atlassian.performance.tools.hardware.guidance.JiraExplorationGuidance
import com.atlassian.performance.tools.infrastructure.api.distribution.PublicJiraSoftwareDistribution
import com.atlassian.performance.tools.lib.LogConfigurationFactory
import com.atlassian.performance.tools.lib.s3cache.S3Cache
import com.atlassian.performance.tools.lib.workspace.GitRepo2
import com.atlassian.performance.tools.virtualusers.api.TemporalRate
import org.apache.logging.log4j.core.config.ConfigurationFactory
import org.eclipse.jgit.api.Git
import org.junit.Test
import java.io.File
import java.time.Duration

class HardwareRecommendationIT {

    private val taskName = "QUICK-132-fix-v3"
    private val workspace = IntegrationTestRuntime.rootWorkspace.isolateTask(taskName)

    @Test
    fun shouldRecommendHardware() {
        ConfigurationFactory.setConfigurationFactory(LogConfigurationFactory(workspace))
        requireCleanRepo()
        val aws = prepareAws()
        val jswVersion = System.getProperty("hwr.jsw.version") ?: "7.13.0"
        val engine = HardwareRecommendationEngine(
            product = PublicJiraSoftwareDistribution(jswVersion),
            scale = ApplicationScales().extraLarge(jiraVersion = jswVersion, postgres = false),
            jiraExploration = JiraExplorationGuidance(
                instanceTypes = listOf(
                    C52xlarge,
                    C54xlarge,
                    C48xlarge,
                    C59xlarge,
                    C518xlarge
                ),
                maxNodeCount = 16,
                minNodeCountForAvailability = 3,
                minApdexGain = 0.01,
                minThroughputGain = TemporalRate(5.0, Duration.ofSeconds(1)),
                db = M44xlarge
            ),
            dbInstanceTypes = listOf(
                M42xlarge,
                M44xlarge,
                M410xlarge,
                M416xlarge
            ),
            maxErrorRate = 0.01,
            minApdex = 0.70,
            repeats = 2,
            aws = aws,
            workspace = workspace,
            s3Cache = S3Cache(
                transfer = TransferManagerBuilder.standard()
                    .withS3Client(aws.s3)
                    .build(),
                bucketName = "quicksilver-jhwr-cache-ireland",
                cacheKey = taskName,
                localPath = workspace.directory
            ),
            explorationCache = HardwareExplorationResultCache(workspace.directory.resolve("processed-cache.json"))
        )
        engine.recommend()
    }

    private fun requireCleanRepo() {
        val status = Git(GitRepo2.findInAncestors(File(".").absoluteFile)).status().call()
        if (status.isClean.not()) {
            throw Exception("Your Git repo is not clean. Please commit the changes and consider pushing them.")
        }
    }
}
