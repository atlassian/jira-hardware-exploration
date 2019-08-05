package com.atlassian.performance.tools.lib

import com.atlassian.performance.tools.jiraactions.api.ActionMetric
import com.atlassian.performance.tools.jiraactions.api.ActionResult

class ErrorGauge {

    fun measureOverall(
        metrics: List<ActionMetric>
    ): OverallError {
        val errors = metrics.count { it.result == ActionResult.ERROR }
        val all = metrics.count()
        return OverallError(Ratio(errors, all))
    }

    fun measureMaxAction(
        metrics: List<ActionMetric>
    ): ActionError = measureActions(metrics)
        .maxBy { it.ratio }
        ?: throw Exception("There's no max action error")

    internal fun measureActions(
        metrics: List<ActionMetric>
    ): List<ActionError> = metrics
        .groupBy { it.label }
        .map { (actionLabel, metricsGroup) ->
            ActionError(
                actionLabel = actionLabel,
                ratio = Ratio(
                    dividend = metricsGroup.count { it.result == ActionResult.ERROR },
                    divisor = metricsGroup.count()
                )
            )
        }
}

class OverallError(
    val ratio: Ratio
) {
    override fun toString() = "${ratio.percent} % metrics errored"
}

class ActionError(
    val actionLabel: String,
    val ratio: Ratio
) {
    override fun toString() = "${ratio.percent} % of $actionLabel errored"
}

/**
 * @param [proportion] Between 0 and 1.
 */
class Ratio(
    val proportion: Double
) : Comparable<Ratio> {
    constructor(
        dividend: Number,
        divisor: Number
    ) : this(dividend.toDouble() / divisor.toDouble())

    /**
     * Between 0% and 100%.
     */
    val percent = proportion * 100

    override fun compareTo(other: Ratio): Int = compareBy<Ratio> { it.proportion }.compare(this, other)

    override fun toString(): String = "$percent %"
}
