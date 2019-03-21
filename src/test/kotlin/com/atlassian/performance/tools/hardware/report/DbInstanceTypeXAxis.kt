package com.atlassian.performance.tools.hardware.report

import com.amazonaws.services.ec2.model.InstanceType
import com.atlassian.performance.tools.hardware.HardwareTestResult

class DbInstanceTypeXAxis : HardwareXAxis<InstanceType> {
    override fun getX(it: HardwareTestResult): InstanceType = it.hardware.db
}
