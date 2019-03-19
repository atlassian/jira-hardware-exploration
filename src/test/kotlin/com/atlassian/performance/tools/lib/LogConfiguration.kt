package com.atlassian.performance.tools.lib

import com.atlassian.performance.tools.workspace.api.TaskWorkspace
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.core.appender.ConsoleAppender
import org.apache.logging.log4j.core.appender.FileAppender
import org.apache.logging.log4j.core.config.AbstractConfiguration
import org.apache.logging.log4j.core.config.ConfigurationSource
import org.apache.logging.log4j.core.config.LoggerConfig
import org.apache.logging.log4j.core.layout.PatternLayout
import java.nio.file.Path
import java.nio.file.Paths

internal class LogConfiguration(
    private val workspace: TaskWorkspace
) : AbstractConfiguration(null, ConfigurationSource.NULL_SOURCE) {

    override fun doConfigure() {
        listOf(
            logToFile(
                name = "com.atlassian.performance.tools.hardware",
                path = Paths.get("hardware-exploration.log")
            ).also { log ->
                log.addAppender(
                    KConsoleAppenderBuilder()
                        .withName("console")
                        .withLayout(layout("%d{ABSOLUTE} %highlight{%-5level} %x %msg%n"))
                        .build(),
                    Level.INFO,
                    null
                )
            },
            logToFile(
                name = "com.atlassian.performance.tools.jvmtasks.api.TaskTimer",
                path = Paths.get("timing.log"),
                pattern = "%d{ISO8601}{UTC}Z <%t> %X %x %msg%n"
            ).also { log ->
                log.addAppender(
                    KConsoleAppenderBuilder()
                        .withName("console")
                        .withLayout(layout("%d{ABSOLUTE} %highlight{%-5level} %x %msg%n"))
                        .build(),
                    Level.ALL,
                    null
                )
            },
            logToFile(
                name = "com.atlassian.performance.tools.aws",
                path = Paths.get("aws.log")
            ),
            logToFile(
                name = "com.atlassian.performance.tools.awsinfrastructure",
                path = Paths.get("aws-infra.log")
            ),
            logToFile(
                name = "com.atlassian.performance.tools.ssh",
                path = Paths.get("ssh.log")
            ),
            logToFile(
                name = "com.atlassian.performance.tools.aws.api.SshKeyFile",
                path = Paths.get("ssh-login.log")
            ),
            logToFile(
                name = "com.atlassian.performance.tools",
                path = Paths.get("detailed.log")
            )
        ).forEach { addLogger(it.name, it) }
    }

    private fun logToFile(
        name: String,
        path: Path,
        pattern: String = "%d{ISO8601}{UTC}Z %-5level <%t> %X %x [%logger] %msg%n"
    ): LoggerConfig {
        val log = LoggerConfig(
            name,
            Level.DEBUG,
            true
        )
        val absolutePath = workspace
            .directory
            .resolve(path)
            .toAbsolutePath()
        log.addAppender(
            KFileAppenderBuilder()
                .withName(absolutePath.fileName.toString())
                .withLayout(layout(pattern))
                .withFileName(absolutePath.toString())
                .withAppend(true)
                .build(),
            Level.DEBUG,
            null
        )
        return log
    }

    private fun layout(
        pattern: String
    ): PatternLayout = PatternLayout.newBuilder()
        .withPattern(pattern)
        .withConfiguration(this)
        .build()
}

private class KFileAppenderBuilder : FileAppender.Builder<KFileAppenderBuilder>()
private class KConsoleAppenderBuilder : ConsoleAppender.Builder<KConsoleAppenderBuilder>()