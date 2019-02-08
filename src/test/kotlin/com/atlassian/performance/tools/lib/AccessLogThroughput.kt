package com.atlassian.performance.tools.lib

import com.atlassian.performance.tools.io.api.directories
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.stream.Stream

class AccessLogThroughput {

    private val logger: Logger = LogManager.getLogger(this::class.java)

    fun gauge(
        rawResults: File
    ): Throughput {
        return rawResults
            .directories()
            .filter { it.name.startsWith("jira-node") }
            .map { gaugeNode(it) }
            .fold(Throughput.ZERO, Throughput::plus)
    }

    private fun gaugeNode(
        rawNodeResults: File
    ): Throughput {
        val logs = rawNodeResults
            .listFiles { file: File -> file.name.startsWith("access_log") }
            .sortedBy { it.name }
        val readers = logs.map { it.bufferedReader() }
        try {
            val allLines = readers
                .map { it.lines() }
                .fold(Stream.empty()) { next: Stream<String>, total ->
                    Stream.concat(next, total)
                }
            return gaugeLines(allLines)
        } finally {
            readers.forEach { it.close() }
        }
    }

    private fun gaugeLines(
        lines: Stream<String>
    ): Throughput {
        var firstEntry: AccessLogEntry? = null
        var lastEntry: AccessLogEntry? = null
        var count = 0
        for (line in lines) {
            count++
            val entry = try {
                parse(line)
            } catch (e: Exception) {
                logger.warn("Cannot parse line $count: `$line`")
                continue
            }
            if (firstEntry == null) {
                if (line.contains("login.jsp")) {
                    firstEntry = entry
                }
            }
            lastEntry = entry
        }
        val span = Duration.between(
            firstEntry?.timestamp ?: throw Exception("There is no first entry"),
            lastEntry?.timestamp ?: throw Exception("There is no last entry")
        )
        return Throughput(count.toDouble(), span).scalePeriod(Duration.ofSeconds(1))
    }

    private fun parse(
        accessLogLine: String
    ): AccessLogEntry {
        val format = DateTimeFormatter.ofPattern("dd/MMM/yyyy:HH:mm:ss Z")
        val match = Regex("\\[(.*?)]").find(accessLogLine)
            ?: throw Exception("Failed to parse access log line: `$accessLogLine`")
        val timestamp = match.groupValues[1]
        return AccessLogEntry(
            timestamp = ZonedDateTime.parse(timestamp, format).toInstant()
        )
    }

}

private class AccessLogEntry(
    val timestamp: Instant
)