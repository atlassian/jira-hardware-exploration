package com.atlassian.performance.tools.lib

import com.atlassian.performance.tools.io.api.directories
import com.atlassian.performance.tools.virtualusers.api.TemporalRate
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.io.File
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.stream.Stream

class AccessLogThroughput {

    private val logger: Logger = LogManager.getLogger(this::class.java)

    fun gauge(
        rawResults: File
    ): TemporalRate {
        return rawResults
            .directories()
            .filter { it.name.startsWith("jira-node") }
            .map { gaugeNode(it) }
            .fold(ZERO_RATE, TemporalRate::plus)
    }

    private fun gaugeNode(
        rawNodeResults: File
    ): TemporalRate {
        val logs = rawNodeResults
            .listFiles { file: File -> file.name.startsWith("access_log.") }
            .sortedBy { it.name }
            .map { AccessLogFile(it) }
        logs.zipWithNext { a, b ->
            val gap = a.estimateGap(b)
            if (gap > Duration.ofDays(1)) {
                throw Exception("There's a big gap ($gap) between $a and $b in $rawNodeResults")
            }
        }
        val readers = logs.map { it.file.bufferedReader() }
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
    ): TemporalRate {
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
        return TemporalRate(count.toDouble(), span).scaleTime(Duration.ofSeconds(1))
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

private class AccessLogFile(
    val file: File
) {
    private val day = file
        .name
        .removePrefix("access_log.")
        .let { LocalDate.parse(it) }
    private val earliestPossibleEntry = day.atTime(LocalTime.MIN)
    private val latestPossibleEntry = day.atTime(LocalTime.MAX)

    fun estimateGap(
        other: AccessLogFile
    ): Duration {
        return Duration.between(latestPossibleEntry, other.earliestPossibleEntry).abs()
    }

    override fun toString(): String {
        return file.name
    }
}
