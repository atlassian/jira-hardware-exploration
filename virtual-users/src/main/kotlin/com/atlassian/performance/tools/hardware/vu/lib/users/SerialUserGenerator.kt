package com.atlassian.performance.tools.hardware.vu.lib.users

import com.atlassian.performance.tools.jiraactions.api.memories.User
import com.atlassian.performance.tools.virtualusers.api.VirtualUserOptions
import com.atlassian.performance.tools.virtualusers.api.users.UserGenerator

class SerialUserGenerator(
    private val userGenerator: UserGenerator
) : UserGenerator {

    private val lock = Object()

    override fun generateUser(
        options: VirtualUserOptions
    ): User = synchronized(lock) {
        userGenerator.generateUser(options)
    }
}
