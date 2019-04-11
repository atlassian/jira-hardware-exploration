package com.atlassian.performance.tools.hardware.report

import com.atlassian.performance.tools.hardware.HardwareExplorationResult
import com.atlassian.performance.tools.hardware.HardwareTestResult
import com.atlassian.performance.tools.io.api.ensureParentDirectory
import com.atlassian.performance.tools.lib.chart.Chart
import com.atlassian.performance.tools.lib.chart.ChartLine
import com.atlassian.performance.tools.lib.chart.ErrorBar
import com.atlassian.performance.tools.lib.chart.Point
import com.atlassian.performance.tools.lib.chart.color.Color
import com.atlassian.performance.tools.lib.chart.color.PresetLabelColor
import com.atlassian.performance.tools.workspace.api.git.GitRepo
import org.apache.logging.log4j.LogManager
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode.HALF_UP
import java.nio.file.Path

/**
 * @param [S] series value type
 * @param [X] X axis value type
 */
internal class HardwareExplorationChart<S, X>(
    private val seriesGrouping: HardwareSeriesGrouping<S>,
    private val xAxis: HardwareXAxis<X>,
    private val repo: GitRepo
) where X : Comparable<X> {
    private val logger = LogManager.getLogger(this::class.java)
    private val adgSecondaryPalette = listOf(
        Color(255, 86, 48),
        Color(255, 171, 0),
        Color(54, 179, 126),
        Color(0, 184, 217),
        Color(101, 84, 192)
    )
    private val presetLabelColor: PresetLabelColor = PresetLabelColor(adgSecondaryPalette)
    private val mathContext = MathContext(3, HALF_UP)

    fun plot(
        results: List<HardwareExplorationResult>,
        application: String,
        output: Path
    ) {
        val testResults = results.mapNotNull { it.testResult }
        if (testResults.isEmpty()) {
            return
        }
        val resultsPerSeries = testResults
            .let { seriesGrouping.group(it) }
            .mapValues { (_, testResults) -> testResults.sortedBy { xAxis.getX(it) } }
        val report = HardwareExplorationChart::class
            .java
            .getResourceAsStream("/hardware-exploration-chart-template.html")
            .bufferedReader()
            .use { it.readText() }
            .replace(
                oldValue = "'<%= apdexChartData =%>'",
                newValue = plotApdex(resultsPerSeries).toJson().toString()
            )
            .replace(
                oldValue = "'<%= maxApdex =%>'",
                newValue = "1.00"
            )
            .replace(
                oldValue = "'<%= errorRateChartData =%>'",
                newValue = plotErrorRate(resultsPerSeries).toJson().toString()
            )
            .replace(
                oldValue = "'<%= maxErrorRate =%>'",
                newValue = testResults.flatMap { it.errorRates }.map { it * 100 }.maxAxis()
            )
            .replace(
                oldValue = "'<%= throughputChartData =%>'",
                newValue = plotThroughput(resultsPerSeries).toJson().toString()
            )
            .replace(
                oldValue = "'<%= maxThroughput =%>'",
                newValue = testResults.flatMap { it.httpThroughputs }.map { it.change }.maxAxis()
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
        resultsPerSeries: Map<S, List<HardwareTestResult>>
    ): Chart<X> = resultsPerSeries
        .map { (series, testResults) ->
            chartLine(
                data = testResults.map {
                    HardwarePoint(
                        x = xAxis.getX(it),
                        value = it.apdex.toBigDecimal(mathContext)
                    )
                },
                errorBars = testResults.map { result ->
                    plotErrorBar(
                        x = xAxis.getX(result),
                        y = result.apdex,
                        yRange = result.apdexes
                    )
                },
                label = series.toString()
            )
        }
        .let { Chart(it) }

    private fun chartLine(
        data: List<Point<X>>,
        errorBars: List<ErrorBar>,
        label: String
    ): ChartLine<X> {
        val labelColor = presetLabelColor
        return ChartLine(
            data = data,
            errorBars = errorBars,
            label = label,
            type = "line",
            hidden = false,
            yAxisId = "y-axis-0",
            labelColor = labelColor
        )
    }

    private fun plotErrorRate(
        resultsPerSeries: Map<S, List<HardwareTestResult>>
    ): Chart<X> = resultsPerSeries
        .map { (series, testResults) ->
            chartLine(
                data = testResults.map {
                    HardwarePoint(
                        x = xAxis.getX(it),
                        value = it.errorRate.times(100).toBigDecimal(mathContext)
                    )
                },
                errorBars = testResults.map { result ->
                    plotErrorBar(
                        x = xAxis.getX(result),
                        y = result.errorRate * 100,
                        yRange = result.errorRates.map { it * 100 }
                    )
                },
                label = series.toString()
            )
        }
        .let { Chart(it) }

    private fun plotThroughput(
        resultsPerSeries: Map<S, List<HardwareTestResult>>
    ): Chart<X> = resultsPerSeries
        .map { (series, testResults) ->
            chartLine(
                data = testResults.map {
                    HardwarePoint(
                        x = xAxis.getX(it),
                        value = it.httpThroughput.change.toBigDecimal(mathContext)
                    )
                },
                errorBars = testResults.map { result ->
                    plotErrorBar(
                        x = xAxis.getX(result),
                        y = result.httpThroughput.change,
                        yRange = result.httpThroughputs.map { throughput -> throughput.change }
                    )
                },
                label = series.toString()
            )
        }
        .let { Chart(it) }

    private fun plotErrorBar(
        x: X,
        y: Double,
        yRange: List<Double>
    ): ErrorBar = HardwareErrorBar(
        x = x,
        plus = yRange.max()!!.minus(y).toBigDecimal(mathContext),
        minus = yRange.min()!!.minus(y).toBigDecimal(mathContext)
    )

    private fun List<Double>.maxAxis(): String = max()!!.toString()
}

private class HardwarePoint<X>(
    override val x: X,
    value: BigDecimal
) : Point<X> {
    override val y: BigDecimal = value
    override fun labelX(): String = x.toString()
}

private class HardwareErrorBar<X>(
    private val x: X,
    override val plus: BigDecimal,
    override val minus: BigDecimal
) : ErrorBar {
    override fun labelX(): String = x.toString()
}
