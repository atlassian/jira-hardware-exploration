package com.atlassian.performance.tools.hardware.vu.lib.users

import com.atlassian.performance.tools.jiraactions.api.memories.User
import com.atlassian.performance.tools.jvmtasks.api.Backoff
import com.atlassian.performance.tools.jvmtasks.api.ExponentialBackoff
import com.atlassian.performance.tools.jvmtasks.api.IdempotentAction
import com.atlassian.performance.tools.virtualusers.api.VirtualUserOptions
import com.atlassian.performance.tools.virtualusers.api.users.RestUserGenerator
import com.atlassian.performance.tools.virtualusers.api.users.UserGenerator
import java.time.Duration

class RetryingUserGenerator(
    private val userGenerator: UserGenerator,
    private val maxAttempts: Int,
    private val backoff: Backoff
) : UserGenerator {

    constructor() : this(
        userGenerator = RestUserGenerator(Duration.ofSeconds(5)),
        maxAttempts = 5,
        backoff = ExponentialBackoff(Duration.ofSeconds(1))
    )

    override fun generateUser(
        options: VirtualUserOptions
    ): User = IdempotentAction("generate users") {
        userGenerator.generateUser(options)
    }.retry(maxAttempts, backoff)
}
