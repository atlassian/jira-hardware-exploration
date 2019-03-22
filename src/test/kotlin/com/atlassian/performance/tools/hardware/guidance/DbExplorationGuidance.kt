package com.atlassian.performance.tools.hardware.guidance

import com.amazonaws.services.ec2.model.InstanceType
import com.atlassian.performance.tools.hardware.*
import com.atlassian.performance.tools.hardware.report.DbInstanceTypeXAxis
import com.atlassian.performance.tools.hardware.report.HardwareExplorationChart
import com.atlassian.performance.tools.hardware.report.HardwareExplorationTable
import com.atlassian.performance.tools.hardware.report.JiraClusterGrouping
import com.atlassian.performance.tools.workspace.api.TaskWorkspace
import com.atlassian.performance.tools.workspace.api.git.GitRepo
import java.util.concurrent.Future

class DbExplorationGuidance(
    private val dbs: List<InstanceType>,
    private val jiraRecommendations: List<HardwareTestResult>,
    private val jiraExploration: List<HardwareExplorationResult>,
    private val jiraOrder: List<InstanceType>,
    private val resultsCache: HardwareExplorationResultCache
) : ExplorationGuidance {

    override fun space(): List<Hardware> = jiraRecommendations.flatMap { jiraRecommendation ->
        dbs.map { db ->
            Hardware(
                jiraRecommendation.hardware.jira,
                jiraRecommendation.hardware.nodeCount,
                db
            )
        }
    }

    override fun decideTesting(
        hardware: Hardware,
        benchmark: (Hardware) -> Future<HardwareExplorationResult>
    ): HardwareExplorationDecision = HardwareExplorationDecision(
        hardware = hardware,
        worthExploring = true,
        reason = "We're exploring all DBs preemptively"
    )

    override fun report(
        results: List<HardwareExplorationResult>,
        task: TaskWorkspace,
        title: String
    ) = synchronized(this) {
        val mergedResults = jiraExploration + results
        resultsCache.write(mergedResults)
        val sortedResults = mergedResults.sortedWith(
            compareBy<HardwareExplorationResult> {
                jiraOrder.indexOf(it.decision.hardware.jira)
            }.thenComparing(
                compareBy<HardwareExplorationResult> {
                    it.decision.hardware.nodeCount
                }
            ).thenComparing(
                compareBy<HardwareExplorationResult> {
                    dbs.indexOf(it.decision.hardware.db)
                }
            )
        )
        HardwareExplorationTable().summarize(
            results = sortedResults,
            table = task.isolateReport("merged-exploration-table.csv")
        )
        HardwareExplorationChart(
            JiraClusterGrouping(jiraOrder),
            DbInstanceTypeXAxis(),
            GitRepo.findFromCurrentDirectory()
        ).plot(
            results = mergedResults,
            application = title,
            output = task.isolateReport("db-exploration-chart.html")
        )
    }
}
