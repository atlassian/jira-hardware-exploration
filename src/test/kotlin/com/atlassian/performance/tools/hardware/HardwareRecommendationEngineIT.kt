package com.atlassian.performance.tools.hardware

import com.amazonaws.services.ec2.model.InstanceType
import com.amazonaws.services.ec2.model.InstanceType.*
import com.amazonaws.services.s3.transfer.TransferManagerBuilder
import com.atlassian.performance.tools.concurrency.api.submitWithLogContext
import com.atlassian.performance.tools.hardware.guidance.JiraExplorationGuidance
import com.atlassian.performance.tools.hardware.tuning.HeapTuning
import com.atlassian.performance.tools.hardware.tuning.JiraNodeTuning
import com.atlassian.performance.tools.hardware.tuning.NoTuning
import com.atlassian.performance.tools.infrastructure.api.distribution.PublicJiraSoftwareDistribution
import com.atlassian.performance.tools.lib.LogConfigurationFactory
import com.atlassian.performance.tools.lib.s3cache.S3Cache
import com.atlassian.performance.tools.virtualusers.api.TemporalRate
import com.atlassian.performance.tools.workspace.api.TaskWorkspace
import com.atlassian.performance.tools.workspace.api.git.GitRepo
import org.apache.logging.log4j.core.config.ConfigurationFactory
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.util.concurrent.Executors

class HardwareRecommendationEngineIT {

    private val cacheKey = "HardwareRecommendationEngineIT/" + GitRepo.findFromCurrentDirectory().getHead()
    private val workspace = IntegrationTestRuntime.rootWorkspace.isolateTask(cacheKey)
    private val jswVersion = "7.13.0"

    @Before
    fun setUp() {
        ConfigurationFactory.setConfigurationFactory(LogConfigurationFactory(workspace))
    }

    @Test
    fun shouldRunHardwareRecommendation() {
        val executor = Executors.newCachedThreadPool()

        val xl = executor.submitWithLogContext("XL") {
            recommend(
                scale = ApplicationScales().extraLarge(jiraVersion = jswVersion, postgres = false),
                tuning = HeapTuning(50),
                db = M44xlarge,
                // test the hard node limit kicks in before the (relaxed) behaviour limits
                minApdexGain = 0.01,
                minThroughputGain = TemporalRate(2.0, Duration.ofSeconds(1)),
                maxNodeCount = 3
            )
        }

        val l = executor.submitWithLogContext("L") {
            recommend(
                scale = ApplicationScales().large(jiraVersion = jswVersion),
                tuning = NoTuning(),
                db = M42xlarge,
                // test the behaviour limits kick in before the (high) hard node limit
                minApdexGain = 0.10,
                minThroughputGain = TemporalRate(5.0, Duration.ofSeconds(1)),
                maxNodeCount = 8
            )
        }

        xl.get()
        l.get()
    }

    private fun recommend(
        scale: ApplicationScale,
        tuning: JiraNodeTuning,
        db: InstanceType,
        minApdexGain: Double,
        minThroughputGain: TemporalRate,
        maxNodeCount: Int
    ): RecommendationSet {
        val aws = IntegrationTestRuntime.prepareAws()
        val scaleWorkspace = TaskWorkspace(workspace.directory.resolve(scale.cacheKey))
        val engine = HardwareRecommendationEngine(
            product = PublicJiraSoftwareDistribution(jswVersion),
            scale = scale,
            tuning = tuning,
            jiraExploration = JiraExplorationGuidance(
                instanceTypes = listOf(
                    C54xlarge,
                    C59xlarge
                ),
                minNodeCountForAvailability = 2,
                maxNodeCount = maxNodeCount,
                minApdexGain = minApdexGain,
                minThroughputGain = minThroughputGain,
                db = db
            ),
            dbInstanceTypes = listOf(
                M42xlarge,
                M44xlarge
            ),
            // attempt to ensure some results are 'good' some are 'bad'
            maxErrorRate = 0.10,
            minApdex = 0.40,
            repeats = 2,
            aws = aws,
            workspace = scaleWorkspace,
            s3Cache = S3Cache(
                transfer = TransferManagerBuilder.standard()
                    .withS3Client(aws.s3)
                    .build(),
                bucketName = "quicksilver-jhwr-cache-ireland",
                cacheKey = "$cacheKey/${scale.cacheKey}",
                localPath = scaleWorkspace.directory,
                etags = IntegrationTestRuntime.rootWorkspace.directory.resolve(".etags")
            ),
            explorationCache = HardwareExplorationResultCache(scaleWorkspace.directory.resolve("processed-cache.json"))
        )
        return engine.recommend()
    }
}
