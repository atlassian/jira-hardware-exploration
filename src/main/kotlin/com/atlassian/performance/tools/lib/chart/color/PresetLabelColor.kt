package com.atlassian.performance.tools.lib.chart.color

class PresetLabelColor(
    private val palette: List<Color>
) : LabelColor {

    private val labelsSeenSoFar = LinkedHashSet<String>()

    override fun color(label: String): Color {
        labelsSeenSoFar.add(label)
        return palette[labelsSeenSoFar.indexOf(label) % palette.size]
    }
}
