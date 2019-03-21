package com.atlassian.performance.tools.hardware.report

import com.atlassian.performance.tools.hardware.HardwareTestResult

interface HardwareSeriesGrouping<S> {

    fun group(results: List<HardwareTestResult>): Map<S, List<HardwareTestResult>>
}
