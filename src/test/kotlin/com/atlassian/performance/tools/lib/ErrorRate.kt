package com.atlassian.performance.tools.lib

import com.atlassian.performance.tools.jiraactions.api.ActionMetric
import com.atlassian.performance.tools.jiraactions.api.ActionResult

class ErrorRate {

    fun measure(
        metrics: List<ActionMetric>
    ): Double {
        val errors = metrics.count { it.result == ActionResult.ERROR }
        val all = metrics.count()
        return errors.toDouble() / all.toDouble()
    }
}