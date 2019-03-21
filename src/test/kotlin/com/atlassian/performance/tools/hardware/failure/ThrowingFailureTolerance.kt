package com.atlassian.performance.tools.hardware.failure

import com.atlassian.performance.tools.workspace.api.TestWorkspace

class ThrowingFailureTolerance : FailureTolerance {
    override fun handle(failure: Exception, workspace: TestWorkspace) {
        throw Exception("Failed in $workspace", failure)
    }
}