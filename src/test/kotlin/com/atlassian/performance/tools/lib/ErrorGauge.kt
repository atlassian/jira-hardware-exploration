package com.atlassian.performance.tools.lib

import com.atlassian.performance.tools.jiraactions.api.ActionMetric
import com.atlassian.performance.tools.jiraactions.api.ActionResult

class ErrorGauge {

    fun measureOverall(
        metrics: List<ActionMetric>
    ): OverallError {
        val errors = metrics.count { it.result == ActionResult.ERROR }
        val all = metrics.count()
        val ratio = errors.toDouble() / all.toDouble()
        return OverallError(Ratio(ratio))
    }
}

class OverallError(
    val ratio: Ratio
) {
    override fun toString() = "${ratio.percent} % metrics errored"
}

/**
 * @param [proportion] Between 0 and 1.
 */
class Ratio(
    val proportion: Double
) {
    /**
     * Between 0% and 100%.
     */
    val percent = proportion * 100
}
