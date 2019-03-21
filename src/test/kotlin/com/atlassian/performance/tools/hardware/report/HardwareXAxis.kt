package com.atlassian.performance.tools.hardware.report

import com.atlassian.performance.tools.hardware.HardwareTestResult

interface HardwareXAxis<X> {

    fun getX(it: HardwareTestResult): X
}
