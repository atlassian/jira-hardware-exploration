package com.atlassian.performance.tools.lib

import com.atlassian.performance.tools.jiraactions.api.ActionMetric
import com.atlassian.performance.tools.jiraactions.api.ActionResult

/**
 * Shows how often the actions were failing. Treats each action type as equally important.
 * Assumes negativity bias and exposes the most pessimistic case.
 */
class ErrorRate {

    fun measure(
        metrics: List<ActionMetric>
    ): Double = metrics
        .groupBy { it.label }
        .values
        .map { labelMetrics ->
            val errors = labelMetrics.count { it.result == ActionResult.ERROR }
            val all = labelMetrics.count()
            return@map errors.toDouble().div(all)
        }
        .max()
        ?: throw Exception("No max error rate")
}
