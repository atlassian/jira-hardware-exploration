package com.atlassian.performance.tools.hardware

import java.io.File

class ReportedExploration(
    val results: List<HardwareExplorationResult>,
    val reports: List<File>
) {
    operator fun plus(
        other: ReportedExploration
    ): ReportedExploration = ReportedExploration(
        results = this.results + other.results,
        reports = this.reports + other.reports
    )
}
