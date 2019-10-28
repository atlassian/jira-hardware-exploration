package com.atlassian.performance.tools.hardware

import com.amazonaws.services.ec2.model.InstanceType
import com.amazonaws.services.ec2.model.InstanceType.*
import com.amazonaws.services.s3.transfer.TransferManagerBuilder
import com.atlassian.performance.tools.aws.api.Aws
import com.atlassian.performance.tools.awsinfrastructure.api.jira.JiraSoftwareInternalDistribution
import com.atlassian.performance.tools.hardware.aws.HardwareRuntime
import com.atlassian.performance.tools.hardware.aws.HardwareRuntime.rootWorkspace
import com.atlassian.performance.tools.hardware.guidance.JiraExplorationGuidance
import com.atlassian.performance.tools.hardware.tuning.HeapTuning
import com.atlassian.performance.tools.hardware.tuning.JiraNodeTuning
import com.atlassian.performance.tools.hardware.tuning.NoTuning
import com.atlassian.performance.tools.infrastructure.api.distribution.PublicJiraSoftwareDistribution
import com.atlassian.performance.tools.io.api.ensureParentDirectory
import com.atlassian.performance.tools.lib.LogConfigurationFactory
import com.atlassian.performance.tools.lib.OverallError
import com.atlassian.performance.tools.lib.Ratio
import com.atlassian.performance.tools.lib.awsinfrastructure.ProductDistributionChain
import com.atlassian.performance.tools.lib.s3cache.S3Cache
import com.atlassian.performance.tools.lib.workspace.GitRepo2
import com.atlassian.performance.tools.virtualusers.api.TemporalRate
import com.atlassian.performance.tools.workspace.api.TaskWorkspace
import org.apache.logging.log4j.CloseableThreadContext
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.config.ConfigurationFactory
import org.eclipse.jgit.api.Git
import org.junit.Test
import java.io.File
import java.time.Duration
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class HardwareRecommendationIT {

    private val jswVersion = System.getProperty("hwr.jsw.version") ?: "7.13.0"
    private val cacheKey = "JREL-5693-v2-$jswVersion"
    private val workspace = rootWorkspace.isolateTask(cacheKey)

    @Test
    fun shouldRecommendHardware() {
        ConfigurationFactory.setConfigurationFactory(LogConfigurationFactory(workspace))
        requireCleanRepo()
        val aws = HardwareRuntime.prepareAws()
        val cache = cacheOnS3(aws)
        try {
            val largeRecommendations = recommendHardwareForLarge(aws, cache)
            val extraLargeRecommendations = recommendHardwareForExtraLarge(aws, cache)
            zipReports(listOf(largeRecommendations, extraLargeRecommendations))
        } finally {
            cache.upload()
        }
    }

    private fun recommendHardwareForExtraLarge(
        aws: Aws,
        s3Cache: S3Cache
    ): ReportedRecommendations = CloseableThreadContext.push("XL").use {
        recommend(
            scale = ApplicationScales().extraLarge(jiraVersion = jswVersion, postgres = false),
            tuning = HeapTuning(50),
            db = M44xlarge,
            aws = aws,
            cache = s3Cache
        )
    }

    private fun recommendHardwareForLarge(
        aws: Aws,
        cache: S3Cache
    ): ReportedRecommendations = CloseableThreadContext.push("L").use {
        recommend(
            scale = ApplicationScales().large(jiraVersion = jswVersion),
            tuning = NoTuning(),
            db = M42xlarge,
            aws = aws,
            cache = cache
        )
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
        db: InstanceType,
        aws: Aws,
        cache: S3Cache
    ): ReportedRecommendations {
        val scaleWorkspace = TaskWorkspace(workspace.directory.resolve(scale.cacheKey))
        val engine = HardwareRecommendationEngine(
            product = ProductDistributionChain(
                PublicJiraSoftwareDistribution(jswVersion),
                JiraSoftwareInternalDistribution(
                    version = jswVersion,
                    unpackTimeout = Duration.ofSeconds(100)
                )
            ),
            scale = scale,
            tuning = tuning,
            jiraExploration = guideJira(db),
            dbInstanceTypes = listOf(
                M4Xlarge,
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
            s3Cache = cache,
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
        aws: Aws
    ): S3Cache = S3Cache(
        transfer = TransferManagerBuilder.standard()
            .withS3Client(aws.s3)
            .build(),
        bucketName = "quicksilver-jhwr-cache-ireland",
        cacheKey = cacheKey,
        localPath = workspace.directory,
        etags = rootWorkspace.directory.resolve(".etags")
    )

    private fun zipReports(
        recommendations: List<ReportedRecommendations>
    ): File {
        val zip = workspace.directory.resolve("$cacheKey-reports.zip").toFile()
        zip
            .ensureParentDirectory()
            .outputStream()
            .let { ZipOutputStream(it) }
            .use { zipStream ->
                recommendations.forEach { zipReports(it, zipStream) }
            }
        LogManager.getLogger(this::class.java).info("Recommendations zipped in ${zip.toURI()}")
        return zip
    }

    private fun zipReports(
        recommendations: ReportedRecommendations,
        zipStream: ZipOutputStream
    ) {
        recommendations.reports.forEach { report ->
            val entryName = recommendations.description + "/" + report.name
            zipStream.putNextEntry(ZipEntry(entryName))
            report.inputStream().use { reportStream ->
                reportStream.copyTo(zipStream)
            }
            zipStream.closeEntry()
        }
    }
}
