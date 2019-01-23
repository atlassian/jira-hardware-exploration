package com.atlassian.performance.tools.hardware

import com.atlassian.performance.tools.lib.Throughput
import com.atlassian.performance.tools.report.api.result.CohortResult

internal class HardwareTestResult(
    val hardware: Hardware,
    val apdex: Double,
    val apdexSpread: Double,
    val httpThroughput: Throughput,
    val httpThroughputSpread: Throughput,
    val errorRate: Double,
    val errorRateSpread: Double,
    val results: List<CohortResult>
) {
    override fun toString(): String = "HardwareTestResult(" +
        "hardware=$hardware" +
        ", apdex=$apdex" +
        ", apdexSpread=$apdexSpread" +
        ", httpThroughput=$httpThroughput" +
        ", httpThroughputSpread=$httpThroughputSpread" +
        ", errorRate=$errorRate" +
        ", errorRateSpread=$errorRateSpread" +
        ")"
}