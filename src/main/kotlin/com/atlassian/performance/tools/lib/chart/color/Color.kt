package com.atlassian.performance.tools.lib.chart.color

class Color(
    val red: Int,
    val green: Int,
    val blue: Int
) {
    fun toCss(): String = "rgb($red, $green, $blue)"
}