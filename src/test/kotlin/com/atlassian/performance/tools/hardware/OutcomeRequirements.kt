package com.atlassian.performance.tools.hardware

import com.atlassian.performance.tools.lib.OverallError
import com.atlassian.performance.tools.lib.Ratio

class OutcomeRequirements(
    val apdexThreshold: Double,
    val overallErrorThreshold: OverallError,
    val maxActionErrorThreshold: Ratio
) {
    fun areSatisfiedBy(
        result: HardwareTestResult
    ): Boolean {
        return (result.apdex > apdexThreshold)
            && (result.overallError.ratio < overallErrorThreshold.ratio)
            && (result.maxActionError!!.ratio < maxActionErrorThreshold)
    }
}
