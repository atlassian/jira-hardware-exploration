package com.atlassian.performance.tools.hardware

import com.amazonaws.services.ec2.model.InstanceType
import com.atlassian.performance.tools.io.api.ensureParentDirectory
import com.atlassian.performance.tools.lib.chart.Chart
import com.atlassian.performance.tools.lib.chart.ChartLine
import com.atlassian.performance.tools.lib.chart.Point
import com.atlassian.performance.tools.workspace.api.git.GitRepo
import org.apache.logging.log4j.LogManager
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.file.Path

internal class HardwareExplorationChart(
    private val repo: GitRepo
) {
    private val logger = LogManager.getLogger(this::class.java)

    fun plotApdex(
        results: List<HardwareExplorationResult>,
        application: String,
        output: Path
    ) {
        val resultsPerInstanceType = results
            .mapNotNull { it.testResult }
            .groupBy { it.hardware.instanceType }
            .mapValues { (_, testResults) -> testResults.sortedBy { it.hardware.nodeCount } }
        val report = HardwareExplorationChart::class
            .java
            .getResourceAsStream("/hardware-exploration-chart-template.html")
            .bufferedReader()
            .use { it.readText() }
            .replace(
                oldValue = "'<%= apdexChartData =%>'",
                newValue = plotApdex(resultsPerInstanceType).toJson().toString()
            )
            .replace(
                oldValue = "'<%= errorRateChartData =%>'",
                newValue = plotErrorRate(resultsPerInstanceType).toJson().toString()
            )
            .replace(
                oldValue = "<%= commit =%>",
                newValue = repo.getHead()
            )
            .replace(
                oldValue = "<%= application =%>",
                newValue = application
            )
        output.toFile().ensureParentDirectory().printWriter().use { it.print(report) }
        logger.info("Hardware exploration chart available at ${output.toUri()}")
    }

    private fun plotApdex(
        resultsPerInstanceType: Map<InstanceType, List<HardwareTestResult>>
    ): Chart<NodeCount> = resultsPerInstanceType
        .map { (instanceType, testResults) ->
            ChartLine(
                data = testResults.map {
                    HardwarePoint(
                        nodeCount = NodeCount(it.hardware.nodeCount),
                        value = BigDecimal.valueOf(it.apdex).setScale(3, RoundingMode.HALF_UP)
                    )
                },
                label = instanceType.toString(),
                type = "line",
                hidden = false,
                yAxisId = "apdex-axis"
            )
        }
        .let { Chart(it) }

    private fun plotErrorRate(
        resultsPerInstanceType: Map<InstanceType, List<HardwareTestResult>>
    ): Chart<NodeCount> = resultsPerInstanceType
        .map { (instanceType, testResults) ->

            ChartLine(
                data = testResults.map {
                    HardwarePoint(
                        nodeCount = NodeCount(it.hardware.nodeCount),
                        value = BigDecimal.valueOf(it.errorRate * 100).setScale(2, RoundingMode.HALF_UP)
                    )
                },
                label = instanceType.toString(),
                type = "line",
                hidden = false,
                yAxisId = "error-rate-axis"
            )
        }
        .let { Chart(it) }
}

private class HardwarePoint(
    private val nodeCount: NodeCount,
    value: BigDecimal
) : Point<NodeCount> {
    override val x: NodeCount = nodeCount
    override val y: BigDecimal = value
    override fun labelX(): String = nodeCount.toString()
}

private class NodeCount(
    val nodeCount: Int
) : Comparable<NodeCount> {
    override fun compareTo(other: NodeCount): Int = compareBy<NodeCount> { it.nodeCount }.compare(this, other)
    override fun toString(): String = nodeCount.toString()
}