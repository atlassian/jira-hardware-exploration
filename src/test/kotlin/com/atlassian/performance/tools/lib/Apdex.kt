package com.atlassian.performance.tools.lib

import com.atlassian.performance.tools.jiraactions.api.ActionMetric
import com.atlassian.performance.tools.jiraactions.api.ActionResult
import com.atlassian.performance.tools.lib.Apdex.Experience.*
import com.atlassian.performance.tools.report.api.result.EdibleResult
import java.time.Duration

class Apdex {
    private val satisfactoryThreshold = Duration.ofSeconds(1)
    private val tolerableThreshold = Duration.ofSeconds(4)

    fun score(
        metrics: List<ActionMetric>
    ): Double {
        return metrics
            .filter { it.result == ActionResult.OK }
            .map { categorize(it) }
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

    fun findOffenders(
        result: EdibleResult
    ): EdibleResult {
        val offendersByLabel = result
            .actionMetrics
            .filter { it.result == ActionResult.OK }
            .filter { categorize(it) != SATISFACTORY }
            .groupBy { it.label }
        val filteredOffenders = offendersByLabel
            .values
            .maxBy { it.size }
            ?: emptyList()
        return EdibleResult.Builder(result.cohort + "-offenders")
            .actionMetrics(filteredOffenders)
            .systemMetrics(result.systemMetrics)
            .nodeDistribution(result.nodeDistribution)
            .build()
    }

    private enum class Experience(
        val score: Float
    ) {
        SATISFACTORY(1.0f),
        TOLERATING(0.5f),
        FRUSTRATED(0.0f)
    }
}