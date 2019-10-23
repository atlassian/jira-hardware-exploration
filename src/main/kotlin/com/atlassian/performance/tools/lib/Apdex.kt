package com.atlassian.performance.tools.lib

import com.atlassian.performance.tools.jiraactions.api.ActionMetric
import com.atlassian.performance.tools.jiraactions.api.ActionResult
import com.atlassian.performance.tools.lib.Apdex.Experience.*
import java.time.Duration

class Apdex {
    private val satisfactoryThreshold = Duration.ofSeconds(1)
    private val tolerableThreshold = Duration.ofSeconds(4)

    fun score(
        metrics: List<ActionMetric>,
        // retain backwards compatibility where apdex is only across OK results
        filter: (ActionResult) -> Boolean = { it -> it == ActionResult.OK }
    ): Double {
        return averageAllScoredMetrics(
            scoreEachMetric(
                metrics
                    .filter { filter(it.result) })
        )
    }

    fun scoreEachMetric(
        metrics: List<ActionMetric>
    ): List<ScoredActionMetric> {
        return metrics
            .map { ScoredActionMetric(it, categorize(it).score) }
    }

    fun averageAllScoredMetrics(
        scoredMetrics: List<ScoredActionMetric>
    ): Double {
        return scoredMetrics
            .map { it.score }
            .average()
    }

    private fun categorize(
        metric: ActionMetric
    ): Experience = when {
        metric.duration < satisfactoryThreshold -> SATISFACTORY
        metric.duration < tolerableThreshold -> TOLERATING
        else -> FRUSTRATED
    }

    private enum class Experience(
        val score: Float
    ) {
        SATISFACTORY(1.0f),
        TOLERATING(0.5f),
        FRUSTRATED(0.0f)
    }
}

