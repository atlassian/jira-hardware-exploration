package com.atlassian.performance.tools.hardware.report

import com.atlassian.performance.tools.hardware.HardwareExplorationResult
import com.atlassian.performance.tools.hardware.HardwareTestResult
import com.atlassian.performance.tools.hardware.OutcomeRequirements
import com.atlassian.performance.tools.hardware.RecommendationSet
import com.atlassian.performance.tools.io.api.ensureParentDirectory
import com.atlassian.performance.tools.lib.ActionError
import com.atlassian.performance.tools.lib.Ratio
import com.atlassian.performance.tools.lib.chart.*
import com.atlassian.performance.tools.lib.chart.color.Color
import com.atlassian.performance.tools.lib.chart.color.PresetLabelColor
import com.atlassian.performance.tools.workspace.api.git.GitRepo
import org.apache.logging.log4j.LogManager
import java.io.File
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
        exploration: List<HardwareExplorationResult>,
        requirements: OutcomeRequirements,
        application: String,
        output: Path
    ): File? {
        val results = exploration.mapNotNull { it.testResult }
        if (results.isEmpty()) {
            return null
        }
        val resultsPerSeries = results
            .let { seriesGrouping.group(it) }
            .mapValues { (_, resultGroup) -> resultGroup.sortedBy { xAxis.getX(it) } }
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
                oldValue = "'<%= apdexThreshold =%>'",
                newValue = requirements.apdexThreshold.toString()
            )
            .replace(
                oldValue = "'<%= overallErrorChartData =%>'",
                newValue = plotOverallError(resultsPerSeries).toJson().toString()
            )
            .replace(
                oldValue = "'<%= maxOverallError =%>'",
                newValue = results
                    .flatMap { it.overallErrors }
                    .map { it.ratio.percent }
                    .maxAxis()
                    .coerceAtMost(100.0)
                    .toString()
            )
            .replace(
                oldValue = "'<%= overallErrorThreshold =%>'",
                newValue = requirements.overallErrorThreshold.ratio.percent.toString()
            )
            .replace(
                oldValue = "'<%= maxActionErrorChartData =%>'",
                newValue = plotMaxActionError(resultsPerSeries).toJson().toString()
            )
            .replace(
                oldValue = "'<%= maxMaxActionError =%>'",
                newValue = results
                    .flatMap { it.maxActionErrors ?: listOf(ActionError("missing", Ratio(0.0))) }
                    .map { it.ratio.percent }
                    .maxAxis()
                    .coerceAtMost(100.0)
                    .toString()
            )
            .replace(
                oldValue = "'<%= maxActionErrorThreshold =%>'",
                newValue = requirements.maxActionErrorThreshold.percent.toString()
            )
            .replace(
                oldValue = "'<%= throughputChartData =%>'",
                newValue = plotThroughput(resultsPerSeries).toJson().toString()
            )
            .replace(
                oldValue = "'<%= maxThroughput =%>'",
                newValue = results
                    .flatMap { it.httpThroughputs }
                    .map { it.change }
                    .maxAxis()
                    .toString()
            )
            .replace(
                oldValue = "<%= xAxisLabel =%>",
                newValue = xAxis.label
            )
            .replace(
                oldValue = "<%= commit =%>",
                newValue = repo.getHead()
            )
            .replace(
                oldValue = "<%= application =%>",
                newValue = application
            )
        val chart = output.toFile()
        chart.ensureParentDirectory().printWriter().use { it.print(report) }
        logger.info("Hardware exploration chart available at ${chart.toURI()}")
        return chart
    }

    fun plotRecommendation(
        recommendations: RecommendationSet,
        application: String,
        output: Path
    ): File? {
        val results = recommendations.exploration.results.mapNotNull { it.testResult }
        if (results.isEmpty()) {
            return null
        }
        val resultsPerSeries = results
            .let { seriesGrouping.group(it) }
            .mapValues { (_, resultGroup) -> resultGroup.sortedBy { xAxis.getX(it) } }
        val report = HardwareExplorationChart::class
            .java
            .getResourceAsStream("/hardware-recommendation-chart-template.html")
            .bufferedReader()
            .use { it.readText() }
            .replace(
                oldValue = "'<%= apdexChartData =%>'",
                newValue = plotRecommendationByApdex(resultsPerSeries, recommendations.bestApdexAndReliability).toJson().toString()
            )
            .replace(
                oldValue = "'<%= maxApdex =%>'",
                newValue = "1.00"
            )
            .replace(
                oldValue = "'<%= costPerApdexChartData =%>'",
                newValue = plotRecommendationByCostEffectiveness(resultsPerSeries, recommendations.bestCostEffectiveness).toJson().toString()
            )
            .replace(
                oldValue = "'<%= maxApdexPerCost =%>'",
                newValue = results
                    .map { it.apdexPerUsdUpkeep.change }
                    .maxAxis()
                    .times(1.20) // scale further to make the graph more readable
                    .toBigDecimal(mathContext)
                    .toString()
            )
            .replace(
                oldValue = "'<%= maxIndex =%>'",
                newValue = resultsPerSeries.size.toString()
            )
            .replace(
                oldValue = "<%= xAxisLabel =%>",
                newValue = xAxis.label
            )
            .replace(
                oldValue = "<%= application =%>",
                newValue = application
            )
        val chart = output.toFile()
        chart.ensureParentDirectory().printWriter().use { it.print(report) }
        logger.info("Hardware recommendation chart available at ${chart.toURI()}")
        return chart
    }

    private fun plotRecommendationByApdex(
        resultsPerSeries: Map<S, List<HardwareTestResult>>,
        recommendation: HardwareTestResult
    ): Chart<X> = resultsPerSeries
        .map { (series, testResults) ->
            chartLine(
                data = testResults.map {
                    HardwarePoint(
                        x = xAxis.getX(it),
                        value = it.apdex.toBigDecimal(mathContext)
                    )
                },
                errorBars = emptyList(),
                label = series.toString()
            )
        }
        .toMutableList().apply {
            this.add(ChartCircledDots(
                data = listOf(
                    HardwarePoint(
                        x = xAxis.getX(recommendation),
                        value = recommendation.apdex.toBigDecimal(mathContext)
                    )
                ),
                label = "Recommended HW : ${recommendation.hardware}",
                labelColor = when (seriesGrouping) {
                    is JiraInstanceTypeGrouping -> presetLabelColor.color(recommendation.hardware.jira.toString())
                    is JiraClusterGrouping -> presetLabelColor.color(JiraCluster(recommendation.hardware.jira,
                        recommendation.hardware.nodeCount).toString())
                    else -> throw Exception("Unknown grouping : $seriesGrouping")
                }
            ))
        }
        .let { Chart(it) }

    private fun plotRecommendationByCostEffectiveness(
        resultsPerSeries: Map<S, List<HardwareTestResult>>,
        recommendation: HardwareTestResult
    ): Chart<X> = resultsPerSeries
        .map { (series, testResults) ->
            chartLine(
                data = testResults.map {
                    HardwarePoint(
                        x = xAxis.getX(it),
                        value = it.apdexPerUsdUpkeep.change.toBigDecimal(mathContext)
                    )
                },
                errorBars = emptyList(),
                label = series.toString()
            )
        }
        .toMutableList().apply {
            this.add(ChartCircledDots(
                data = listOf(
                    HardwarePoint(
                        x = xAxis.getX(recommendation),
                        value = recommendation.apdexPerUsdUpkeep.change.toBigDecimal(mathContext)
                    )
                ),
                label = "Recommended HW : ${recommendation.hardware}",
                labelColor = when (seriesGrouping) {
                    is JiraInstanceTypeGrouping -> presetLabelColor.color(recommendation.hardware.jira.toString())
                    is JiraClusterGrouping -> presetLabelColor.color(JiraCluster(recommendation.hardware.jira,
                        recommendation.hardware.nodeCount).toString())
                    else -> throw Exception("Unknown grouping : $seriesGrouping")
                }
            ))
        }
        .let { Chart(it) }

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
    ): ChartItem<X> {
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

    private fun plotOverallError(
        resultsPerSeries: Map<S, List<HardwareTestResult>>
    ): Chart<X> = resultsPerSeries
        .map { (series, testResults) ->
            chartLine(
                data = testResults.map {
                    HardwarePoint(
                        x = xAxis.getX(it),
                        value = it.overallError.ratio.percent.toBigDecimal(mathContext)
                    )
                },
                errorBars = testResults.map { result ->
                    plotErrorBar(
                        x = xAxis.getX(result),
                        y = result.overallError.ratio.percent,
                        yRange = result.overallErrors.map { it.ratio.percent }
                    )
                },
                label = series.toString()
            )
        }
        .let { Chart(it) }

    private fun plotMaxActionError(
        resultsPerSeries: Map<S, List<HardwareTestResult>>
    ): Chart<X> = resultsPerSeries
        .filterValues { testResults ->
            testResults.all { testResult ->
                testResult.maxActionError != null
            }
        }
        .map { (series, testResults) ->
            chartLine(
                data = testResults.map {
                    HardwarePoint(
                        x = xAxis.getX(it),
                        value = it.maxActionError!!.ratio.percent.toBigDecimal(mathContext)
                    )
                },
                errorBars = testResults.map { result ->
                    plotErrorBar(
                        x = xAxis.getX(result),
                        y = result.maxActionError!!.ratio.percent,
                        yRange = result.maxActionErrors!!.map { it.ratio.percent }
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

    private fun List<Double>.maxAxis(): Double = max()!!.times(1.15)
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
