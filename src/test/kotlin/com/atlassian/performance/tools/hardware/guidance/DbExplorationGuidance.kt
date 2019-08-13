package com.atlassian.performance.tools.hardware.guidance

import com.amazonaws.services.ec2.model.InstanceType
import com.atlassian.performance.tools.hardware.*
import com.atlassian.performance.tools.hardware.report.DbInstanceTypeXAxis
import com.atlassian.performance.tools.hardware.report.HardwareExplorationChart
import com.atlassian.performance.tools.hardware.report.HardwareExplorationTable
import com.atlassian.performance.tools.hardware.report.JiraClusterGrouping
import com.atlassian.performance.tools.workspace.api.TaskWorkspace
import com.atlassian.performance.tools.workspace.api.git.GitRepo
import java.io.File
import java.util.concurrent.Future

class DbExplorationGuidance(
    private val dbs: List<InstanceType>,
    private val jiraRecommendations: List<HardwareTestResult>,
    private val jiraExploration: List<HardwareExplorationResult>
) : ExplorationGuidance {

    private val instanceTypeOrder = InstanceType.values().toList()

    override fun space(): List<Hardware> = jiraRecommendations.flatMap { jiraRecommendation ->
        dbs.map { db ->
            jiraRecommendation.hardware.let { hardware ->
                Hardware(
                    hardware.jira,
                    hardware.nodeCount,
                    db
                )
            }
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
        exploration: List<HardwareExplorationResult>,
        requirements: OutcomeRequirements,
        task: TaskWorkspace,
        title: String,
        resultsCache: HardwareExplorationResultCache
    ): List<File> = synchronized(this) {
        val mergedExploration = jiraExploration + exploration
        resultsCache.write(mergedExploration)
        val table = HardwareExplorationTable().summarize(
            results = sort(mergedExploration),
            table = task.isolateReport("merged-exploration-table.csv")
        )
        val chart = HardwareExplorationChart(
            JiraClusterGrouping(instanceTypeOrder),
            DbInstanceTypeXAxis(),
            GitRepo.findFromCurrentDirectory()
        ).plot(
            exploration = sort(exploration),
            requirements = requirements,
            application = title,
            output = task.isolateReport("db-exploration-chart.html")
        )
        return listOfNotNull(table, chart)
    }

    private fun sort(
        exploration: List<HardwareExplorationResult>
    ): List<HardwareExplorationResult> = exploration.sortedWith(
        compareBy<HardwareExplorationResult> {
            instanceTypeOrder.indexOf(it.decision.hardware.jira)
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
}
