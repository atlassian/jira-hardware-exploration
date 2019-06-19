package com.atlassian.performance.tools.hardware.report

import com.atlassian.performance.tools.hardware.HardwareExplorationResult
import com.atlassian.performance.tools.hardware.HardwareExplorationResultCache
import com.atlassian.performance.tools.hardware.HardwareTestResult
import org.junit.Test
import java.io.File
import java.nio.file.Path

class ApdexSpreadHypothesisTest {

    /**
     * H1 large error rate spread => large apdex spread
     */
    @Test
    fun highErrorRateSpreadShouldCauseHighApdexSpread() {
        val processedCache = File(javaClass.getResource("/QUICK-132-processed-cache.json").toURI()).toPath()
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
}
