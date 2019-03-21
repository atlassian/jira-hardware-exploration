package com.atlassian.performance.tools.hardware

data class HardwareExplorationDecision(
    val hardware: Hardware,
    val worthExploring: Boolean,
    val reason: String
)