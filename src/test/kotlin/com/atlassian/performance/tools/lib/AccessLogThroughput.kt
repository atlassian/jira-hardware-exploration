package com.atlassian.performance.tools.lib

import com.atlassian.performance.tools.io.api.directories
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

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
        return rawNodeResults
            .listFiles { file: File -> file.name.startsWith("access_log") }
            .map { tryToGaugeAccessLog(it) }
            .fold(Throughput.ZERO, Throughput::plus)
    }

    private fun tryToGaugeAccessLog(
        accessLog: File
    ): Throughput {
        try {
            return gaugeAccessLog(accessLog)
        } catch (e: Exception) {
            throw Exception("Failed to gauge access log: $accessLog", e)
        }
    }

    private fun gaugeAccessLog(
        accessLog: File
    ): Throughput {
        var firstEntry: AccessLogEntry? = null
        var lastEntry: AccessLogEntry? = null
        var count = 0
        accessLog.useLines { lines ->
            for (line in lines) {
                count++
                val entry = try {
                    parse(line)
                } catch (e: Exception) {
                    logger.warn("Cannot parse $accessLog on line $count: `$line`")
                    continue
                }
                if (firstEntry == null) {
                    if (line.contains("login.jsp")) {
                        firstEntry = entry
                    }
                }
                lastEntry = entry
            }
        }
        val span = Duration.between(
            firstEntry?.timestamp ?: throw Exception("$accessLog does not have a first entry"),
            lastEntry?.timestamp ?: throw Exception("$accessLog does not have a last entry")
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