package com.atlassian.performance.tools.hardware.report

import com.atlassian.performance.tools.hardware.HardwareTestResult
import com.atlassian.performance.tools.hardware.RecommendationSet
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter
import java.io.File
import java.nio.file.Path
import java.time.Duration

class RecommendationsTable {

    fun tabulate(
        recommendations: RecommendationSet,
        output: Path
    ): File {
        val headers = arrayOf(
            "reason",
            "jira",
            "jira nodes",
            "database",
            "apdex average (0.0-1.0)",
            "cost [USD/hour]"
        )
        val format = CSVFormat.DEFAULT
            .withHeader(*headers)
            .withRecordSeparator('\n')
        val tableFile = output.toFile()
        tableFile.bufferedWriter().use { writer ->
            val printer = CSVPrinter(writer, format)
            printRecommendation(recommendations.bestApdexAndReliability, "best Apdex and reliabilty", printer)
            printRecommendation(recommendations.bestCostEffectiveness, "most cost-effective", printer)
        }
        return tableFile
    }

    private fun printRecommendation(
        recommendation: HardwareTestResult,
        reason: String,
        printer: CSVPrinter
    ) {
        val hardware = recommendation.hardware
        val cost = hardware.estimateCost()
        printer.printRecord(
            reason,
            hardware.jira,
            hardware.nodeCount,
            hardware.db,
            "%.3f".format(recommendation.apdex),
            "%.2f".format(cost.scaleTime(Duration.ofHours(1)).change)
        )
    }
}
