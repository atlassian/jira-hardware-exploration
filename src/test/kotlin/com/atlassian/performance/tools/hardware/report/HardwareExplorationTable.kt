package com.atlassian.performance.tools.hardware.report

import com.atlassian.performance.tools.hardware.HardwareExplorationResult
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter
import java.nio.file.Path
import java.time.Duration

internal class HardwareExplorationTable {

    fun summarize(
        results: List<HardwareExplorationResult>,
        table: Path
    ) {
        val headers = arrayOf(
            "jira",
            "jira nodes",
            "database",
            "error rate average [%]",
            "error rate spread [%]",
            "apdex average (0.0-1.0)",
            "apdex spread (0.0-1.0)",
            "throughput average [HTTP requests / second]",
            "throughput spread [HTTP requests / second]",
            "worth exploring?",
            "reason"
        )
        val format = CSVFormat.DEFAULT.withHeader(*headers).withRecordSeparator('\n')
        table.toFile().bufferedWriter().use { writer ->
            val printer = CSVPrinter(writer, format)
            results.forEach {
                val result = it.testResult
                val hardware = it.decision.hardware
                val throughputPeriod = Duration.ofSeconds(1)
                if (result != null) {
                    printer.printRecord(
                        hardware.jira,
                        hardware.nodeCount,
                        hardware.db,
                        result.errorRate * 100,
                        result.errorRateSpread * 100,
                        result.apdex,
                        result.apdexSpread,
                        result.httpThroughput.scalePeriod(throughputPeriod).count,
                        result.httpThroughputSpread.scalePeriod(throughputPeriod).count,
                        if (it.decision.worthExploring) "YES" else "NO",
                        it.decision.reason
                    )
                } else {
                    printer.printRecord(
                        hardware.jira,
                        hardware.nodeCount,
                        hardware.db,
                        "-",
                        "-",
                        "-",
                        "-",
                        "-",
                        "-",
                        if (it.decision.worthExploring) "YES" else "NO",
                        it.decision.reason
                    )
                }
            }
        }
    }
}
