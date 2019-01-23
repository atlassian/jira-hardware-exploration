package com.atlassian.performance.tools.hardware

import com.amazonaws.services.ec2.model.InstanceType
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter
import java.nio.file.Path
import java.time.Duration

internal class HardwareExplorationTable {

    fun summarize(
        results: List<HardwareExplorationResult>,
        instanceTypesOrder: List<InstanceType>,
        table: Path
    ) {
        val sortedResults = results.sortedWith(
            compareBy<HardwareExplorationResult> {
                instanceTypesOrder.indexOf(it.decision.hardware.instanceType)
            }.thenComparing(
                compareBy<HardwareExplorationResult> {
                    it.decision.hardware.nodeCount
                }
            )
        )

        val headers = arrayOf(
            "instance type",
            "node count",
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
            sortedResults.forEach {
                val result = it.testResult
                val hardware = it.decision.hardware
                val throughputPeriod = Duration.ofSeconds(1)
                if (result != null) {
                    printer.printRecord(
                        hardware.instanceType,
                        hardware.nodeCount,
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
                        hardware.instanceType,
                        hardware.nodeCount,
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