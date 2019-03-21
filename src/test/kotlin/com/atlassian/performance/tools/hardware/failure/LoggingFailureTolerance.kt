package com.atlassian.performance.tools.hardware.failure

import com.atlassian.performance.tools.workspace.api.TestWorkspace
import org.apache.logging.log4j.Logger

class LoggingFailureTolerance(
    private val logger: Logger
) : FailureTolerance {

    override fun handle(failure: Exception, workspace: TestWorkspace) {
        logger.error("Failure in $workspace, better investigate or remove it")
    }
}