package com.atlassian.performance.tools.lib.chart.color

internal interface LabelColor {

    fun color(label: String): Color
}