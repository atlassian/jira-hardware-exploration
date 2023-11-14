package com.atlassian.performance.tools.hardware.guidance

import com.amazonaws.services.ec2.model.InstanceType
import com.atlassian.performance.tools.hardware.*
import com.atlassian.performance.tools.hardware.report.HardwareExplorationChart
import com.atlassian.performance.tools.hardware.report.HardwareExplorationTable
import com.atlassian.performance.tools.hardware.report.JiraInstanceTypeGrouping
import com.atlassian.performance.tools.hardware.report.NodeCountXAxis
import com.atlassian.performance.tools.lib.minus
import com.atlassian.performance.tools.virtualusers.api.TemporalRate
import com.atlassian.performance.tools.workspace.api.TaskWorkspace
import com.atlassian.performance.tools.workspace.api.git.GitRepo
import java.io.File
import java.util.concurrent.Future

class JiraExplorationGuidance(
    private val instanceTypes: List<InstanceType>,
    private val maxNodeCount: Int,
    private val minNodeCountForAvailability: Int,
    private val minApdexGain: Double,
    private val minThroughputGain: TemporalRate,
    private val db: InstanceType
) : ExplorationGuidance {

    override fun space(): List<Hardware> = instanceTypes.flatMap { instanceType ->
        (1..maxNodeCount).map { Hardware(instanceType, it, db) }.filter { avoidProblematicHardware(it) }
    }

    /**
     * Avoids:
     * ```
     * http://admin:admin@localhost:8080/rest/api/2/upgrade failed to get out of 503 status within PT18M
     * ```
     */
    private fun avoidProblematicHardware(
        hardware: Hardware
    ): Boolean {
        return (hardware.jira == InstanceType.C52xlarge && hardware.nodeCount >= 9).not()
    }

    override fun decideTesting(
        hardware: Hardware,
        benchmark: (Hardware) -> Future<HardwareExplorationResult>
    ): HardwareExplorationDecision {
        if (hardware.nodeCount <= minNodeCountForAvailability) {
            return HardwareExplorationDecision(
                hardware = hardware,
                worthExploring = true,
                reason = "high availability"
            )
        }
        val smallerHardwareTests = (1 until hardware.nodeCount)
            .map { Hardware(hardware.jira, it, db) }
            .map { smallerHardware -> benchmark(smallerHardware) }
        val smallerHardwareResults = try {
            smallerHardwareTests.map { it.get() }
        } catch (e: Exception) {
            return HardwareExplorationDecision(
                hardware = hardware,
                worthExploring = false,
                reason = "testing smaller hardware had failed, ERROR: ${e.message}"
            )
        }
        val sortedPreviousResults = smallerHardwareResults
            .asSequence()
            .mapNotNull { it.testResult }
            .sortedBy { it.hardware.nodeCount }
        val apdexIncrements = sortedPreviousResults
            .map { it.apdex }
            .zipWithNext { a, b -> b - a }
        val throughputIncrements = sortedPreviousResults
            .map { it.httpThroughput }
            .zipWithNext { a, b -> b.minus(a) }
        val apdexBoostedEnough = apdexIncrements.last() > minApdexGain
        val throughputBoostedEnough = throughputIncrements.last() > minThroughputGain
        return when {
            apdexBoostedEnough -> HardwareExplorationDecision(
                hardware = hardware,
                worthExploring = true,
                reason = "adding a node made improved Apdex enough"
            )
            throughputBoostedEnough -> HardwareExplorationDecision(
                hardware = hardware,
                worthExploring = true,
                reason = "adding a node made improved throughput enough"
            )
            else -> HardwareExplorationDecision(
                hardware = hardware,
                worthExploring = false,
                reason = "adding more nodes did not improve Apdex enough"
            )
        }
    }

    override fun report(
        exploration: List<HardwareExplorationResult>,
        requirements: OutcomeRequirements,
        task: TaskWorkspace,
        title: String,
        resultsCache: HardwareExplorationResultCache
    ): List<File> = synchronized(this) {
        resultsCache.write(exploration)
        val sortedResults = exploration.sortedWith(
            compareBy<HardwareExplorationResult> {
                instanceTypes.indexOf(it.decision.hardware.jira)
            }.thenComparing(
                compareBy<HardwareExplorationResult> {
                    it.decision.hardware.nodeCount
                }
            )
        )
        val table = HardwareExplorationTable().summarize(
            results = sortedResults,
            table = task.isolateReport("exploration-table.csv")
        )
        val chart = HardwareExplorationChart(
            JiraInstanceTypeGrouping(compareBy { instanceTypes.indexOf(it) }),
            NodeCountXAxis(),
            GitRepo.findFromCurrentDirectory()
        ).plot(
            exploration = exploration,
            requirements = requirements,
            application = title,
            output = task.isolateReport("jira-exploration-chart.html")
        )
        return listOfNotNull(table, chart)
    }
}
