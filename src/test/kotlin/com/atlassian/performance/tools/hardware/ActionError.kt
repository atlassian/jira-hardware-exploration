package com.atlassian.performance.tools.hardware

import java.math.MathContext

class ActionError(
    val percentage: Double,
    val actionLabel: String
) {
    override fun toString(): String {
        val roundedPercentage = percentage.toBigDecimal(MathContext(2))
        return "$actionLabel failed $roundedPercentage % of the time"
    }
}
