package com.atlassian.performance.tools.hardware.report

import com.amazonaws.services.ec2.model.InstanceType
import com.atlassian.performance.tools.hardware.HardwareTestResult

class JiraInstanceTypeGrouping(
    private val instanceTypeOrder: Comparator<InstanceType>
) : HardwareSeriesGrouping<InstanceType> {

    override fun group(
        results: List<HardwareTestResult>
    ): Map<InstanceType, List<HardwareTestResult>> {
        return results
            .groupBy { it.hardware.jira }
            .toSortedMap(instanceTypeOrder)
    }
}
