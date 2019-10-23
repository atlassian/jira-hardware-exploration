package com.atlassian.performance.tools.hardware.report

import com.amazonaws.services.ec2.model.InstanceType
import com.atlassian.performance.tools.hardware.HardwareTestResult

class JiraClusterGrouping(
    private val instanceTypeOrder: List<InstanceType>
) : HardwareSeriesGrouping<JiraCluster> {

    override fun group(
        results: List<HardwareTestResult>
    ): Map<JiraCluster, List<HardwareTestResult>> {
        return results
            .groupBy { JiraCluster(it.hardware.jira, it.hardware.nodeCount) }
            .toSortedMap(
                compareBy<JiraCluster> {
                    instanceTypeOrder.indexOf(it.jira)
                }.thenComparing(
                    compareBy<JiraCluster> {
                        it.nodeCount
                    }
                )
            )
    }
}

data class JiraCluster(
    val jira: InstanceType,
    val nodeCount: Int
) {
    override fun toString(): String = "$nodeCount x $jira"
}
