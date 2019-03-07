package com.atlassian.performance.tools.hardware

import com.atlassian.performance.tools.aws.api.ProvisionedStack
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.junit.Test

class BambooCleanupIT {

    private val logger: Logger = LogManager.getLogger(this::class.java)
    val aws = IntegrationTestRuntime.aws

    @Test
    fun cleanUp() {
        logger.info("Pulling stacks data...")
        val stacks = aws.listDisposableStacks()
            .map { ProvisionedStack(it, aws) }
            .filter { it.bambooBuild?.startsWith("QUICK-JHWR") ?: false }
        if (stacks.isNotEmpty()) {
            logger.info("Deleting stacks...")
            stacks
                .map { stack ->
                    stack
                        .release()
                        .thenAccept { logger.info("Deleted ${stack.stackName} ") }
                }
                .forEach { it.get() }
            logger.info("All of your stacks have been deleted")
        } else {
            logger.info("No stacks to delete")
        }
    }
}