package com.atlassian.performance.tools.hardware.vu.lib.users

import com.atlassian.performance.tools.jiraactions.api.memories.User
import com.atlassian.performance.tools.virtualusers.api.VirtualUserOptions
import com.atlassian.performance.tools.virtualusers.api.users.UserGenerator
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.time.Duration
import java.time.Instant.now
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class TimeControllingUserGenerator(
    private val targetTime: Duration,
    private val userGenerator: UserGenerator
) : UserGenerator {

    private val logger: Logger = LogManager.getLogger(this::class.java)

    override fun generateUser(
        options: VirtualUserOptions
    ): User {
        val thread = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "time-controlling-user-generator")
        }
        try {
            return controlGenerationTime(thread, options)
        } finally {
            thread.shutdownNow()
        }
    }

    private fun controlGenerationTime(
        thread: ExecutorService,
        options: VirtualUserOptions
    ): User {
        val start = now()
        val futureUser = thread.submit(Callable {
            userGenerator.generateUser(options)
        })
        val user = futureUser.get(targetTime.seconds, TimeUnit.SECONDS)
        val elapsed = Duration.between(start, now())
        val remaining = targetTime - elapsed
        if (remaining > Duration.ZERO) {
            logger.info("User generated in time, with $remaining remaining")
            Thread.sleep(remaining.toMillis())
        }
        return user
    }
}
