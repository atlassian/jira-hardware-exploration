package com.atlassian.performance.tools.lib.jvmtasks

import com.atlassian.performance.tools.jvmtasks.api.Backoff
import java.time.Duration

class StaticBackoff(
    private val backoff: Duration
) : Backoff {

    override fun backOff(attempt: Int): Duration = backoff
}
