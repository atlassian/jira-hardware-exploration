package com.atlassian.performance.tools.hardware

internal data class HardwareExplorationDecision(
    val hardware: Hardware,
    val worthExploring: Boolean,
    val reason: String
)