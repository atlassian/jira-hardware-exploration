package com.atlassian.performance.tools.hardware.failure

import com.atlassian.performance.tools.workspace.api.TestWorkspace

class CleaningFailureTolerance : FailureTolerance {
    override fun handle(failure: Exception, workspace: TestWorkspace) {
        workspace.directory.toFile().deleteRecursively()
    }
}