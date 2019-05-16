package com.atlassian.performance.tools.hardware

import com.atlassian.performance.tools.lib.invert
import com.atlassian.performance.tools.report.api.result.RawCohortResult
import com.atlassian.performance.tools.virtualusers.api.TemporalRate

class HardwareTestResult(
    val hardware: Hardware,
    val apdex: Double,
    val apdexes: List<Double>,
    val httpThroughput: TemporalRate,
    val httpThroughputs: List<TemporalRate>,
    val errorRate: Double,
    val errorRates: List<Double>,
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
        ", errorRate=$errorRate" +
        ", errorRates=$errorRates" +
        ", apdexPerUsdUpkeep=$apdexPerUsdUpkeep" +
        ", usdUpkeep=$usdUpkeep" +
        ")"
}
