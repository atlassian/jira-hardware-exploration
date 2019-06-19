package com.atlassian.performance.tools.lib.chart

import com.atlassian.performance.tools.lib.chart.color.Color
import javax.json.Json
import javax.json.JsonObject

internal class ChartCircledDots<X : Comparable<X>>(
    override val data: List<Point<X>>,
    private val label: String,
    private val pointRadius: String = "20",
    private val pointHoverRadius: String = "20",
    private val borderWidth: String = "3",
    private val hidden: Boolean = false,
    private val labelColor: Color
) : ChartItem<X>(data) {
    override fun toJson(): JsonObject {
        val dataBuilder = Json.createArrayBuilder()
        data.forEach { point ->
            dataBuilder.add(
                Json.createObjectBuilder()
                    .add("x", point.labelX())
                    .add("y", point.y)
                    .build()
            )
        }
        val chartDataBuilder = Json.createObjectBuilder()
        chartDataBuilder.add("label", label)
        chartDataBuilder.add("data", dataBuilder)
        chartDataBuilder.add("hidden", hidden)
        chartDataBuilder.add("type", "line")
        chartDataBuilder.add("borderColor", labelColor.toCss())
        chartDataBuilder.add("backgroundColor", "rgb(255, 255, 255)")
        chartDataBuilder.add("fill", false)
        chartDataBuilder.add("showLine", "false")
        chartDataBuilder.add("pointRadius", pointRadius)
        chartDataBuilder.add("pointHoverRadius", pointHoverRadius)
        chartDataBuilder.add("borderWidth", borderWidth)
        return chartDataBuilder.build()
    }
}

