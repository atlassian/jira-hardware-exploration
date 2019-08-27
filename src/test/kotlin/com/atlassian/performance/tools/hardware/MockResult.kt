package com.atlassian.performance.tools.hardware

import java.io.File

class MockResult(
    private val resourcePath: String
) {

    fun readRecommendations(): RecommendationSet {
        val exploration = readExploration()
        val candidates = exploration
            .mapNotNull { it.testResult }
            .filter { it.apdex > 0.40 }
        return RecommendationSet(
            exploration = ReportedExploration(exploration, emptyList()),
            bestApdexAndReliability = candidates.maxBy { it.apdex }!!,
            bestCostEffectiveness = candidates.maxBy { it.apdexPerUsdUpkeep }!!
        )
    }

    fun readExploration(): List<HardwareExplorationResult> = resourcePath
        .let { javaClass.getResource(it) }
        .toURI()
        .let { File(it) }
        .toPath()
        .let { HardwareExplorationResultCache(it) }
        .read()
}
