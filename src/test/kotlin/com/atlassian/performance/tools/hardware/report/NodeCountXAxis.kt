package com.atlassian.performance.tools.hardware.report

import com.atlassian.performance.tools.hardware.HardwareTestResult

class NodeCountXAxis : HardwareXAxis<NodeCount> {
    override fun getX(it: HardwareTestResult): NodeCount = NodeCount(it.hardware.nodeCount)
}


class NodeCount(
    private val nodeCount: Int
) : Comparable<NodeCount> {
    override fun compareTo(other: NodeCount): Int = compareBy<NodeCount> { it.nodeCount }.compare(this, other)
    override fun toString(): String = nodeCount.toString()
}