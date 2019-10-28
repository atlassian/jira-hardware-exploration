package com.atlassian.performance.tools.hardware

import com.amazonaws.services.ec2.model.InstanceType
import com.atlassian.performance.tools.hardware.RecommendationSet
import com.atlassian.performance.tools.hardware.ReportedExploration
import com.atlassian.performance.tools.hardware.ReportedRecommendations
import com.atlassian.performance.tools.hardware.report.*
import com.atlassian.performance.tools.hardware.report.HardwareExplorationChart
import com.atlassian.performance.tools.jvmtasks.api.TaskTimer
import com.atlassian.performance.tools.workspace.api.git.GitRepo
import java.io.File
import java.nio.file.Path

class HardwareReportEngine {
    fun reportedRecommendations(description : String, outputPath: Path, jiraRecommendations: RecommendationSet, dbRecommendations: RecommendationSet, jiraExploration: ReportedExploration, dbExploration: ReportedExploration): ReportedRecommendations {
        val jiraReport = reportJiraRecommendation(description, jiraRecommendations, outputPath)

        val dbRecommendationChart = chartDbRecommendation(description, dbRecommendations, outputPath)
        val dbRecommendationTable = tabularize(dbRecommendations, outputPath)

        return ReportedRecommendations(
            description = description,
            recommendations = dbRecommendations,
            reports = listOfNotNull(jiraReport, dbRecommendationChart, dbRecommendationTable)
                + jiraExploration.reports
                + dbExploration.reports
        )
    }

    private fun reportJiraRecommendation(
        description: String,
        recommendations: RecommendationSet,
        outputPath: Path
    ) = HardwareExplorationChart(
        JiraInstanceTypeGrouping(compareBy { InstanceType.values().toList().indexOf(it) }),
        NodeCountXAxis(),
        GitRepo.findFromCurrentDirectory()
    ).plotRecommendation(
        recommendations = recommendations,
        application = description,
        output = outputPath.resolve("jira-recommendation-chart.html")
    )

    private fun chartDbRecommendation(
        description: String,
        recommendations: RecommendationSet,
        outputPath: Path
    ) = HardwareExplorationChart(
        JiraClusterGrouping(InstanceType.values().toList()),
        DbInstanceTypeXAxis(),
        GitRepo.findFromCurrentDirectory()
    ).plotRecommendation(
        recommendations = RecommendationSet(
            exploration = ReportedExploration(
                results = recommendations.exploration.results.sortedWith(
                    compareBy<HardwareExplorationResult> {
                        InstanceType.values().toList().indexOf(it.decision.hardware.jira)
                    }.thenComparing(
                        compareBy<HardwareExplorationResult> {
                            it.decision.hardware.nodeCount
                        }
                    ).thenComparing(
                        compareBy<HardwareExplorationResult> {
                            InstanceType.values().toList().indexOf(it.decision.hardware.db)
                        }
                    )
                ),
                reports = recommendations.exploration.reports
            ),
            bestApdexAndReliability = recommendations.bestApdexAndReliability,
            bestCostEffectiveness = recommendations.bestCostEffectiveness
        ),
        application = description,
        output = outputPath.resolve("db-recommendation-chart.html")
    )

    private fun tabularize(
        recommendations: RecommendationSet,
        outputPath: Path
    ): File {
        return RecommendationsTable().tabulate(
            recommendations = recommendations,
            output = outputPath.resolve("db-recommendation-table.csv")
        )
    }
}
