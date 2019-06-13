package com.atlassian.performance.tools.hardware

import com.amazonaws.services.ec2.model.InstanceType.C59xlarge
import com.amazonaws.services.ec2.model.InstanceType.M44xlarge
import com.amazonaws.services.s3.transfer.TransferManagerBuilder
import com.atlassian.performance.tools.hardware.IntegrationTestRuntime.aws
import com.atlassian.performance.tools.hardware.IntegrationTestRuntime.taskName
import com.atlassian.performance.tools.hardware.IntegrationTestRuntime.workspace
import com.atlassian.performance.tools.hardware.guidance.SingleHardwareGuidance
import com.atlassian.performance.tools.infrastructure.api.distribution.PublicJiraSoftwareDistribution
import com.atlassian.performance.tools.lib.s3cache.S3Cache
import com.atlassian.performance.tools.lib.workspace.GitRepo2
import org.eclipse.jgit.api.Git
import org.junit.Test
import java.io.File

class HardwareRecommendationIT {

    @Test
    fun shouldRecommendHardware() {
        requireCleanRepo()
        val jswVersion = "8.1.0"
        val engine = HardwareRecommendationEngine(
            product = PublicJiraSoftwareDistribution(jswVersion),
            scale = ApplicationScales().extraLarge(jiraVersion = jswVersion, postgres = true),
            jiraExploration = SingleHardwareGuidance(
                Hardware(
                    jira = C59xlarge,
                    nodeCount = 7,
                    db = M44xlarge)),
            dbInstanceTypes = listOf(
                M44xlarge
            ),
            maxErrorRate = 0.01,
            minApdex = 0.70,
            repeats = 2,
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
