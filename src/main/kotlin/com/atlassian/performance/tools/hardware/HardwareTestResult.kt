package com.atlassian.performance.tools.hardware

import com.atlassian.performance.tools.lib.ActionError
import com.atlassian.performance.tools.lib.OverallError
import com.atlassian.performance.tools.lib.invert
import com.atlassian.performance.tools.report.api.result.RawCohortResult
import com.atlassian.performance.tools.virtualusers.api.TemporalRate

/**
 * @param [maxActionError] Might be null in old cached processed results.
 * @param [maxActionErrors] Might be null in old cached processed results.
 */
class HardwareTestResult(
    val hardware: Hardware,
    val apdex: Double,
    val apdexes: List<Double>,
    val httpThroughput: TemporalRate,
    val httpThroughputs: List<TemporalRate>,
    val overallError: OverallError,
    val overallErrors: List<OverallError>,
    val maxActionError: ActionError?,
    val maxActionErrors: List<ActionError>?,
    val results: List<RawCohortResult>
) {

    val usdUpkeep: TemporalRate = hardware.estimateCost()
    val apdexPerUsdUpkeep: TemporalRate = usdUpkeep
        .invert()
        .times(apdex)

    override fun toString(): String = "HardwareTestResult(" +
        "hardware=$hardware" +
        ", apdex=$apdex" +
        ", apdexes=$apdexes" +
        ", httpThroughput=$httpThroughput" +
        ", httpThroughputs=$httpThroughputs" +
        ", overallError=$overallError" +
        ", overallErrors=$overallErrors" +
        ", maxActionError=$maxActionError" +
        ", maxActionErrors=$maxActionErrors" +
        ", apdexPerUsdUpkeep=$apdexPerUsdUpkeep" +
        ", usdUpkeep=$usdUpkeep" +
        ")"
}
