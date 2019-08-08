package com.atlassian.performance.tools.hardware.vu.lib.jvmtasks

import com.atlassian.performance.tools.jvmtasks.api.Backoff
import java.time.Duration
import java.util.*

class JitterBackoff(
    private val minimum: Backoff,
    private val maxJitter: Duration
) : Backoff {

    private val random = Random()

    override fun backOff(attempt: Int): Duration {
        return minimum.backOff(attempt) + jitter()
    }

    private fun jitter(): Duration {
        val jitterMillis = maxJitter.toMillis() * random.nextDouble()
        return Duration.ofMillis(jitterMillis.toLong())
    }
}
