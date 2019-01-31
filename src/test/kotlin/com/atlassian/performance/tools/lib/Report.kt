package com.atlassian.performance.tools.lib

import com.atlassian.performance.tools.io.api.resolveSafely
import com.atlassian.performance.tools.report.api.result.RawCohortResult
import com.atlassian.performance.tools.workspace.api.TestWorkspace
import java.io.File
import java.nio.file.Path

fun TestWorkspace.readResult(cohort: String): RawCohortResult {
    val statusText = getStatusText()
    val factory = RawCohortResult.Factory()
    return when (statusText) {
        "OK" -> factory.fullResult(
            cohort = cohort,
            results = digOutTheRawResults(cohort).toPath()
        )
        null -> {
            factory.failedResult(
                cohort = cohort,
                failure = Exception("Test for $cohort terminated abruptly"),
                results = directory
            )
        }
        else -> factory.failedResult(
            cohort = cohort,
            failure = Exception("Test for $cohort failed due to an error: $statusText"),
            results = directory
        )
    }
}

fun TestWorkspace.writeStatus(
    results: RawCohortResult
) {
    val failure = results.failure
    val statusText = if (failure != null) {
        "FAILED: $failure"
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
    .toExistingFile()
    ?: throw Exception("The raw results for $cohort are missing in $directory")

