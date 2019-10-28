package com.atlassian.performance.tools.hardware

import com.atlassian.performance.tools.lib.OverallError
import com.atlassian.performance.tools.lib.Ratio

class OutcomeRequirements(
    val apdexThreshold: Double,
    val overallErrorThreshold: OverallError,
    val maxActionErrorThreshold: Ratio,
    val reliabilityRange: Int = 1
) {
    fun areSatisfiedBy(
        result: HardwareTestResult
    ): Boolean {
        return (result.apdex > apdexThreshold)
            && (result.overallError.ratio < overallErrorThreshold.ratio)
            && (result.maxActionError!!.ratio < maxActionErrorThreshold)
    }

    fun areReliable(
        result: HardwareTestResult,
        results: List<HardwareTestResult>
    ): Boolean {
        val hardwareAfterIncident = result.hardware.copy(
            nodeCount = result.hardware.nodeCount - reliabilityRange
        )
        return results
            .map { it.hardware }
            .contains(hardwareAfterIncident)
    }
}
