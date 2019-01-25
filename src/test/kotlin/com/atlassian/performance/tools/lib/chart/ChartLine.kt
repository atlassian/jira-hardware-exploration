package com.atlassian.performance.tools.lib.chart

import com.atlassian.performance.tools.lib.chart.color.LabelColor
import com.atlassian.performance.tools.lib.chart.color.SeedLabelColor
import java.math.BigDecimal
import javax.json.Json
import javax.json.JsonObject

internal class ChartLine<X>(
    val data: List<Point<X>>,
    private val label: String,
    private val type: String,
    private val yAxisId: String,
    private val hidden: Boolean = false,
    private val errorBars: List<ErrorBar> = emptyList(),
    private val labelColor: LabelColor = SeedLabelColor()
) where X : Comparable<X> {
    fun toJson(): JsonObject {
        val dataBuilder = Json.createArrayBuilder()
        data.forEach { point ->
            dataBuilder.add(
                Json.createObjectBuilder()
                    .add("x", point.labelX())
                    .add("y", point.y)
                    .build()
            )
        }
        val errorBarBuilder = Json.createObjectBuilder()
        errorBars.forEach { errorBar ->
            errorBarBuilder.add(
                errorBar.labelX(),
                Json.createObjectBuilder()
                    .add("plus", errorBar.plus)
                    .add("minus", errorBar.minus)
            )
        }
        val chartDataBuilder = Json.createObjectBuilder()
        chartDataBuilder.add("type", type)
        chartDataBuilder.add("label", label)
        val color = labelColor.color(label).toCss()
        chartDataBuilder.add("borderColor", color)
        chartDataBuilder.add("backgroundColor", color)
        chartDataBuilder.add("fill", false)
        chartDataBuilder.add("data", dataBuilder)
        chartDataBuilder.add("errorBars", errorBarBuilder)
        chartDataBuilder.add("yAxisID", yAxisId)
        chartDataBuilder.add("hidden", hidden)
        chartDataBuilder.add("lineTension", 0)

        return chartDataBuilder.build()
    }
}

internal interface ErrorBar {
    val plus: BigDecimal
    val minus: BigDecimal
    fun labelX(): String
}
