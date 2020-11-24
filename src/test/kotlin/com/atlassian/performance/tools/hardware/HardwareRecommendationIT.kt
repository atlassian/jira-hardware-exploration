package com.atlassian.performance.tools.hardware

import com.amazonaws.services.ec2.model.InstanceType
import com.amazonaws.services.ec2.model.InstanceType.*
import com.amazonaws.services.s3.transfer.TransferManagerBuilder
import com.atlassian.performance.tools.aws.api.Aws
import com.atlassian.performance.tools.awsinfrastructure.api.jira.JiraSoftwareInternalDistribution
import com.atlassian.performance.tools.hardware.IntegrationTestRuntime.rootWorkspace
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

    private val jswVersion = System.getProperty("hwr.jsw.version") ?: "8.13.1"
    private val cacheKey = "QUICK-2143-$jswVersion"
    private val workspace = rootWorkspace.isolateTask(cacheKey)

    @Test
    fun shouldRecommendHardware() {
        ConfigurationFactory.setConfigurationFactory(LogConfigurationFactory(workspace))
        requireCleanRepo()
        val aws = IntegrationTestRuntime.prepareAws()
        val cache = cacheOnS3(aws)
        try {
            val largeDataLargeLoadRecommendations = recommendHardwareForLargeDataLargeLoad(aws, cache)
            val largeDataExtraLargeLoadRecommendations = recommendHardwareForLargeDataExtraLargeLoad(aws, cache)
            val extraLargeDataLargeLoadRecommendations = recommendHardwareForExtraLargeDataLargeLoad(aws, cache)
            val extraLargeDataExtraLargeLoadRecommendations = recommendHardwareForExtraLargeDataExtraLargeLoad(aws, cache)
            zipReports(listOf(largeDataLargeLoadRecommendations, largeDataExtraLargeLoadRecommendations,
                extraLargeDataLargeLoadRecommendations, extraLargeDataExtraLargeLoadRecommendations))
        } finally {
            cache.upload()
        }
    }

    private fun recommendHardwareForExtraLargeDataExtraLargeLoad(
        aws: Aws,
        s3Cache: S3Cache
    ): ReportedRecommendations = CloseableThreadContext.push("XLXL").use {
        recommend(
            scale = ApplicationScales().extraLargeDataExtraLargeLoad(jiraVersion = jswVersion, postgres = false),
            tuning = HeapTuning(50),
            db = M44xlarge,
            aws = aws,
            cache = s3Cache,
            dbInstanceTypes = listOf(
                M44xlarge
            )
        )
    }

    private fun recommendHardwareForExtraLargeDataLargeLoad(
        aws: Aws,
        s3Cache: S3Cache
    ): ReportedRecommendations = CloseableThreadContext.push("XLL").use {
        recommend(
            scale = ApplicationScales().extraLargeDateLargeLoad(jiraVersion = jswVersion, postgres = false),
            tuning = HeapTuning(50),
            db = M44xlarge,
            aws = aws,
            cache = s3Cache,
            dbInstanceTypes = listOf(
                M44xlarge
            )
        )
    }

    private fun recommendHardwareForLargeDataLargeLoad(
        aws: Aws,
        cache: S3Cache
    ): ReportedRecommendations = CloseableThreadContext.push("LL").use {
        recommend(
            scale = ApplicationScales().largeDataLargeLoad(jiraVersion = jswVersion),
            tuning = HeapTuning(50),
            db = M42xlarge,
            aws = aws,
            cache = cache,
            dbInstanceTypes = listOf(
                M42xlarge
            )
        )
    }

    private fun recommendHardwareForLargeDataExtraLargeLoad(
        aws: Aws,
        cache: S3Cache
    ): ReportedRecommendations = CloseableThreadContext.push("LXL").use {
        recommend(
            scale = ApplicationScales().largeDataExtraLargeLoad(jiraVersion = jswVersion),
            tuning = HeapTuning(50),
            db = M42xlarge,
            aws = aws,
            cache = cache,
            dbInstanceTypes = listOf(
                M42xlarge
            )
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
        cache: S3Cache,
        dbInstanceTypes: List<InstanceType>
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
            dbInstanceTypes = dbInstanceTypes,
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
        etags = IntegrationTestRuntime.rootWorkspace.directory.resolve(".etags")
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
