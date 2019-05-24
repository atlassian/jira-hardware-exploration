package com.atlassian.performance.tools.hardware.report

import com.atlassian.performance.tools.hardware.HardwareExplorationResult
import com.atlassian.performance.tools.hardware.HardwareExplorationResultCache
import com.atlassian.performance.tools.hardware.HardwareTestResult
import org.junit.Test
import java.nio.file.Path
import java.nio.file.Paths

class ApdexSpreadHypothesisTest {


    /**
     * H1 large error rate spread => large apdex spread
     */
    @Test
    fun highErrorRateSpreadShouldCauseHighApdexSpread() {
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
        val falsePositives = healthy.intersect(diagnosed)
        val falseNegatives = sick.intersect(undiagnosed)

        val sensitivity = truePositives.size.toDouble() / sick.size.toDouble()
        val specificity = trueNegatives.size.toDouble() / healthy.size.toDouble()
        return TestQuality(sensitivity, specificity)
    }

    private data class TestQuality(
        val sensitivity: Double,
        val specificity: Double
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
