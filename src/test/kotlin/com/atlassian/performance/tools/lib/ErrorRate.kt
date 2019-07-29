package com.atlassian.performance.tools.lib

import com.atlassian.performance.tools.hardware.ActionError
import com.atlassian.performance.tools.jiraactions.api.ActionMetric
import com.atlassian.performance.tools.jiraactions.api.ActionResult

/**
 * Shows how often the actions were failing. Treats each action type as equally important.
 * Assumes negativity bias and exposes the most pessimistic case.
 */
class ErrorRate {

    fun measureMaxPerAction(
        metrics: List<ActionMetric>
    ): ActionError = metrics
        .groupBy { it.label }
        .map { (label, metricsGroup) ->
            val errors = metricsGroup.count { it.result == ActionResult.ERROR }
            val all = metricsGroup.count()
            return@map ActionError(
                percentage = errors
                    .times(100)
                    .toDouble()
                    .div(all),
                actionLabel = label
            )
        }
        .maxBy { it.percentage }
        ?: throw Exception("No max error rate")
}
