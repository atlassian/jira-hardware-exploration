package com.atlassian.performance.tools.hardware.failure

import com.atlassian.performance.tools.workspace.api.TestWorkspace
import org.apache.logging.log4j.Logger

class BugAwareTolerance(
    private val logger: Logger
) : FailureTolerance {

    val cleaning = CleaningFailureTolerance()
    val logging = LoggingFailureTolerance(logger)

    override fun handle(failure: Exception, workspace: TestWorkspace) {
        when {
            causedByJperf387(failure) -> cleanAfterKnownIssue(failure, workspace, "JPERF-387")
            causedByJperf382(failure) -> cleanAfterKnownIssue(failure, workspace, "JPERF-382")
            causedByAbruptness(failure) -> cleanWithJustification(failure, workspace, "it didn't finish")
            else -> logging.handle(failure, workspace)
        }
    }

    private fun causedByJperf382(
        failure: Exception
    ): Boolean = failure
        .message!!
        .contains("java.net.SocketTimeoutException: Read timed out")

    private fun causedByJperf387(
        failure: Exception
    ): Boolean = failure
        .message!!
        .contains("Failed to install")

    private fun cleanAfterKnownIssue(
        failure: Exception,
        workspace: TestWorkspace,
        issueKey: String
    ) {
        cleanWithJustification(
            failure,
            workspace,
            "it is a known issue: https://ecosystem.atlassian.net/browse/$issueKey"
        )
    }

    private fun cleanWithJustification(
        failure: Exception,
        workspace: TestWorkspace,
        justification: String
    ) {
        cleaning.handle(failure, workspace)
        logger.warn("Cleaned failure in $workspace because $justification")
    }

    private fun causedByAbruptness(
        failure: Exception
    ): Boolean = failure
        .message!!
        .endsWith("terminated abruptly")
}
