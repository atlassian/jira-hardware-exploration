package com.atlassian.performance.tools.lib

import com.atlassian.performance.tools.jiraactions.api.ActionMetric
import com.atlassian.performance.tools.jiraactions.api.ActionResult
import com.atlassian.performance.tools.jiraactions.api.ActionType
import java.time.Duration

class Apdex {
    private val satisfactoryThreshold = Duration.ofSeconds(1)
    private val tolerableThreshold = Duration.ofSeconds(4)

    fun score(
        metrics: List<ActionMetric>
    ): Double {
        return metrics
            .filter { it.result == ActionResult.OK }
            .map { score(it) }
            .average()
    }

    private fun score(
        metric: ActionMetric
    ): Float = when {
        metric.duration < satisfactoryThreshold -> 1.0f
        metric.duration < tolerableThreshold -> 0.5f
        else -> 0.0f
    }
}