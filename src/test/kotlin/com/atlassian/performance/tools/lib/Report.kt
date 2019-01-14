package com.atlassian.performance.tools.lib

import com.atlassian.performance.tools.io.api.directories
import com.atlassian.performance.tools.io.api.resolveSafely
import com.atlassian.performance.tools.jiraactions.api.parser.MergingActionMetricsParser
import com.atlassian.performance.tools.report.api.parser.MergingNodeCountParser
import com.atlassian.performance.tools.report.api.parser.SystemMetricsParser
import com.atlassian.performance.tools.report.api.result.CohortResult
import com.atlassian.performance.tools.report.api.result.EdibleResult
import com.atlassian.performance.tools.report.api.result.FailedCohortResult
import com.atlassian.performance.tools.report.api.result.FullCohortResult
import com.atlassian.performance.tools.workspace.api.TestWorkspace
import java.io.File
import java.nio.file.Path

fun TestWorkspace.readResult(cohort: String): CohortResult {
    val statusText = getStatusText()
    return when (statusText) {
        "OK" -> digOutTheCohortResult(cohort)
        null -> FailedCohortResult(
            cohort = cohort,
            failure = Exception("Test for $cohort terminated abruptly")
        )
        else -> FailedCohortResult(
            cohort = cohort,
            failure = Exception("Test for $cohort failed due to an error: $statusText")
        )
    }
}

fun TestWorkspace.writeStatus(
    results: EdibleResult
) {
    val statusText = if (results.failure != null) {
        "FAILED: ${results.failure}"
    } else {
        "OK"
    }
    getStatusFile().write { it.write(statusText) }
}

private fun TestWorkspace.getStatusFile(): Path = directory.resolve("status.txt")

private fun TestWorkspace.getStatusText(): String? = getStatusFile()
    .toExistingFile()
    ?.bufferedReader()
    ?.use { it.readText() }
    ?.trim()

fun TestWorkspace.digOutTheRawResults(
    cohort: String
): File = directory
    .resolveSafely(cohort)
    .toFile()
    .directories()
    .single()
    .directories()
    .single()
    .toPath()
    .toExistingFile()
    ?: throw Exception("The raw results for $cohort are missing in $directory")

private fun TestWorkspace.digOutTheCohortResult(
    cohort: String
): CohortResult = FullCohortResult(
    cohort = cohort,
    results = digOutTheRawResults(cohort).toPath(),
    actionParser = MergingActionMetricsParser(),
    systemParser = SystemMetricsParser(),
    nodeParser = MergingNodeCountParser()
)