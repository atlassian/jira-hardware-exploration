package com.atlassian.performance.tools.lib.chart

import javax.json.JsonObject

internal abstract class ChartItem<X>(open val data: List<Point<X>>) where X : Comparable<X> {
    abstract fun toJson(): JsonObject
}
