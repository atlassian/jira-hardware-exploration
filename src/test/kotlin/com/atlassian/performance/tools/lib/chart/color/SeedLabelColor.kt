package com.atlassian.performance.tools.lib.chart.color

import java.util.*

class SeedLabelColor : LabelColor {
    override fun color(label: String): Color {
        val random = Random(label.hashCode().toLong())
        return Color(
            red = random.nextInt(255),
            green = random.nextInt(255),
            blue = random.nextInt(255)
        )
    }
}