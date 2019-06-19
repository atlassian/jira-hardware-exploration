package com.atlassian.performance.tools.hardware.report

import com.amazonaws.services.s3.transfer.TransferManagerBuilder
import com.atlassian.performance.tools.aws.api.Aws
import com.atlassian.performance.tools.hardware.*
import com.atlassian.performance.tools.jvmtasks.api.TaskTimer
import com.atlassian.performance.tools.lib.s3cache.S3Cache
import org.junit.Test
import java.nio.file.Path
import java.nio.file.Paths

class ApdexSpreadHypothesisTest {

    private val taskName = "QUICK-132-fix-v3"
    private val workspace = IntegrationTestRuntime.rootWorkspace.isolateTask(taskName)

    /**
     * H1 large error rate spread => large apdex spread
     */
    @Test
    fun highErrorRateSpreadShouldCauseHighApdexSpread() {

        // https://docs.oracle.com/javase/tutorial/essential/io/fileOps.html#glob
        // processed-cache.json contain the pre-processed information
        val downloadFileFilter = "**/processed-cache.json"
        val cache = getWorkspaceCache(IntegrationTestRuntime.prepareAws(), downloadFileFilter)

        // get the files
        TaskTimer.time("download") { cache.download() }

        val processedCache = Paths.get("build/jpt-workspace/QUICK-132-fix-v3/processed-cache.json")
        val exploration = readExploration(processedCache)
        val testResults = exploration.mapNotNull { it.testResult }
        val highApdexSpread = testResults.filter { it.apdexSpread() > 0.03 }
        val highErrorRateSpread = testResults.filter { it.errorRateSpread() > 0.01 }

        val quality = measureQuality(
            population = testResults,
            sick = highApdexSpread,
            diagnosed = highErrorRateSpread
        )

        println("quality = $quality")
    }

    private fun measureQuality(
        population: List<*>,
        sick: List<*>,
        diagnosed: List<*>
    ): TestQuality {
        val healthy = population - sick
        val undiagnosed = population - diagnosed

        val truePositives = sick.intersect(diagnosed)
        val trueNegatives = healthy.intersect(undiagnosed)

        // never used but kept here to provide a reference for future use.
        @Suppress("UNUSED_VARIABLE") val falsePositives = healthy.intersect(diagnosed)
        @Suppress("UNUSED_VARIABLE") val falseNegatives = sick.intersect(undiagnosed)

        val tp = truePositives.size.toDouble()
        val tn = trueNegatives.size.toDouble()
        val p = sick.size.toDouble()
        val n = healthy.size.toDouble()
        return TestQuality(
            sensitivity = tp / p,
            specificity = tn / n,
            accuracy = (tp + tn) / (p + n),
            prevalence = p / (p + n)
        )
    }

    private data class TestQuality(
        val sensitivity: Double,
        val specificity: Double,
        val accuracy: Double,
        val prevalence: Double
    )

    private fun readExploration(
        resourcePath: Path
    ): List<HardwareExplorationResult> = HardwareExplorationResultCache(resourcePath).read()

    private fun HardwareTestResult.apdexSpread(): Double {
        return apdexes.max()!! - apdexes.min()!!
    }

    private fun HardwareTestResult.errorRateSpread(): Double {
        return errorRates.max()!! - errorRates.min()!!
    }

    private fun getWorkspaceCache(aws: Aws, @Suppress("SameParameterValue") searchPattern: String) = S3Cache(
        transfer = TransferManagerBuilder.standard()
            .withS3Client(aws.s3)
            .build(),
        bucketName = "quicksilver-jhwr-cache-ireland",
        cacheKey = taskName,
        localPath = workspace.directory,
        searchPattern = searchPattern
    )
}
