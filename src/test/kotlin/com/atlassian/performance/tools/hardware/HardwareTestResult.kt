package com.atlassian.performance.tools.hardware

import com.atlassian.performance.tools.lib.Throughput
import com.atlassian.performance.tools.report.api.result.EdibleResult

internal class HardwareTestResult(
    val hardware: Hardware,
    val apdex: Double,
    val httpThroughput: Throughput,
    val errorRate: Double,
    val results: List<EdibleResult>
) {
    override fun toString(): String = "HardwareTestResult(" +
        "hardware=$hardware" +
        ", apdex=$apdex" +
        ", httpThroughput=$httpThroughput" +
        ", errorRate=$errorRate" +
        ")"
}