package com.atlassian.performance.tools.hardware.vu.lib.users

import com.atlassian.performance.tools.hardware.vu.lib.jvmtasks.JitterBackoff
import com.atlassian.performance.tools.jiraactions.api.memories.User
import com.atlassian.performance.tools.jvmtasks.api.ExponentialBackoff
import com.atlassian.performance.tools.virtualusers.api.VirtualUserOptions
import com.atlassian.performance.tools.virtualusers.api.users.RestUserGenerator
import com.atlassian.performance.tools.virtualusers.api.users.TimeControllingUserGenerator
import com.atlassian.performance.tools.virtualusers.api.users.UserGenerator
import java.time.Duration

class PredictableRestUserGenerator : UserGenerator {

    private val userGenerator = TimeControllingUserGenerator(
        targetTime = Duration.ofMinutes(2),
        userGenerator = RetryingUserGenerator(
            userGenerator = RestUserGenerator(Duration.ofSeconds(20)),
            maxAttempts = 5,
            backoff = JitterBackoff(
                minimum = ExponentialBackoff(Duration.ofSeconds(2)),
                maxJitter = Duration.ofSeconds(3)
            )
        )
    )

    override fun generateUser(
        options: VirtualUserOptions
    ): User {
        return userGenerator.generateUser(options)
    }
}
