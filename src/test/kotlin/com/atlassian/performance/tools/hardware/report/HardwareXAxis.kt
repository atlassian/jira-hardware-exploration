package com.atlassian.performance.tools.hardware.report

import com.atlassian.performance.tools.hardware.HardwareTestResult

interface HardwareXAxis<X> {

    val label: String
    fun getX(it: HardwareTestResult): X
}
