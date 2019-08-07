package com.atlassian.performance.tools.hardware

import com.amazonaws.services.ec2.model.InstanceType
import com.amazonaws.services.ec2.model.InstanceType.*
import com.amazonaws.services.s3.transfer.TransferManagerBuilder
import com.atlassian.performance.tools.aws.api.Aws
import com.atlassian.performance.tools.hardware.guidance.JiraExplorationGuidance
import com.atlassian.performance.tools.hardware.tuning.HeapTuning
import com.atlassian.performance.tools.hardware.tuning.JiraNodeTuning
import com.atlassian.performance.tools.hardware.tuning.NoTuning
import com.atlassian.performance.tools.infrastructure.api.distribution.PublicJiraSoftwareDistribution
import com.atlassian.performance.tools.lib.LogConfigurationFactory
import com.atlassian.performance.tools.lib.OverallError
import com.atlassian.performance.tools.lib.Ratio
import com.atlassian.performance.tools.lib.s3cache.S3Cache
import com.atlassian.performance.tools.lib.workspace.GitRepo2
import com.atlassian.performance.tools.virtualusers.api.TemporalRate
import com.atlassian.performance.tools.workspace.api.TaskWorkspace
import org.apache.logging.log4j.CloseableThreadContext
import org.apache.logging.log4j.core.config.ConfigurationFactory
import org.eclipse.jgit.api.Git
import org.junit.Test
import java.io.File
import java.time.Duration

class HardwareRecommendationIT {

    private val cacheKey = "QUICK-240-redo-jsw-7.13.0"
    private val workspace = IntegrationTestRuntime.rootWorkspace.isolateTask(cacheKey)
    private val jswVersion = System.getProperty("hwr.jsw.version") ?: "7.13.0"

    @Test
    fun shouldRecommendHardware() {
        ConfigurationFactory.setConfigurationFactory(LogConfigurationFactory(workspace))
        requireCleanRepo()
        recommendHardwareForLarge()
        recommendHardwareForExtraLarge()
    }

    private fun recommendHardwareForExtraLarge() {
        CloseableThreadContext.push("XL").use {
            recommend(
                scale = ApplicationScales().extraLarge(jiraVersion = jswVersion, postgres = false),
                tuning = HeapTuning(50),
                db = M44xlarge
            )
        }
    }

    private fun recommendHardwareForLarge() {
        CloseableThreadContext.push("L").use {
            recommend(
                scale = ApplicationScales().large(jiraVersion = jswVersion),
                tuning = NoTuning(),
                db = M42xlarge
            )
        }
    }

    private fun requireCleanRepo() {
        val status = Git(GitRepo2.findInAncestors(File(".").absoluteFile)).status().call()
        if (status.isClean.not()) {
            throw Exception("Your Git repo is not clean. Please commit the changes and consider pushing them.")
        }
    }

    private fun recommend(
        scale: ApplicationScale,
        tuning: JiraNodeTuning,
        db: InstanceType
    ): RecommendationSet {
        val aws = IntegrationTestRuntime.prepareAws()
        val scaleWorkspace = TaskWorkspace(workspace.directory.resolve(scale.cacheKey))
        val engine = HardwareRecommendationEngine(
            product = PublicJiraSoftwareDistribution(jswVersion),
            scale = scale,
            tuning = tuning,
            jiraExploration = guideJira(db),
            dbInstanceTypes = listOf(
                M42xlarge,
                M44xlarge,
                M410xlarge,
                M416xlarge
            ),
            requirements = OutcomeRequirements(
                overallErrorThreshold = OverallError(Ratio(0.01)),
                maxActionErrorThreshold = Ratio(0.05),
                apdexThreshold = 0.70
            ),
            repeats = 2,
            aws = aws,
            workspace = scaleWorkspace,
            s3Cache = cacheOnS3(aws, scale, scaleWorkspace),
            explorationCache = HardwareExplorationResultCache(
                scaleWorkspace.directory.resolve("processed-cache.json")
            )
        )
        return engine.recommend()
    }

    private fun guideJira(
        db: InstanceType
    ): JiraExplorationGuidance = JiraExplorationGuidance(
        instanceTypes = listOf(
            C52xlarge,
            C54xlarge,
            C48xlarge,
            C59xlarge,
            C518xlarge
        ),
        minNodeCountForAvailability = 3,
        maxNodeCount = 16,
        minApdexGain = 0.01,
        minThroughputGain = TemporalRate(5.0, Duration.ofSeconds(1)),
        db = db
    )

    private fun cacheOnS3(
        aws: Aws,
        scale: ApplicationScale,
        scaleWorkspace: TaskWorkspace
    ): S3Cache = S3Cache(
        transfer = TransferManagerBuilder.standard()
            .withS3Client(aws.s3)
            .build(),
        bucketName = "quicksilver-jhwr-cache-ireland",
        cacheKey = "$cacheKey/${scale.cacheKey}",
        localPath = scaleWorkspace.directory,
        etags = IntegrationTestRuntime.rootWorkspace.directory.resolve(".etags")
    )
}
