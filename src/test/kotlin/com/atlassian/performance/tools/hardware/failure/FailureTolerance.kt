package com.atlassian.performance.tools.hardware.failure

import com.atlassian.performance.tools.workspace.api.TestWorkspace

interface FailureTolerance {
    fun handle(failure: Exception, workspace: TestWorkspace)
}