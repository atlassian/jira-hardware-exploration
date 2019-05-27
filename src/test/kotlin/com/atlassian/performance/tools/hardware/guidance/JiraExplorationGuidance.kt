package com.atlassian.performance.tools.hardware.guidance

import com.amazonaws.services.ec2.model.InstanceType
import com.atlassian.performance.tools.hardware.Hardware
import com.atlassian.performance.tools.hardware.HardwareExplorationDecision
import com.atlassian.performance.tools.hardware.HardwareExplorationResult
import com.atlassian.performance.tools.hardware.HardwareExplorationResultCache
import com.atlassian.performance.tools.hardware.report.HardwareExplorationChart
import com.atlassian.performance.tools.hardware.report.HardwareExplorationTable
import com.atlassian.performance.tools.hardware.report.JiraInstanceTypeGrouping
import com.atlassian.performance.tools.hardware.report.NodeCountXAxis
import com.atlassian.performance.tools.lib.minus
import com.atlassian.performance.tools.virtualusers.api.TemporalRate
import com.atlassian.performance.tools.workspace.api.TaskWorkspace
import com.atlassian.performance.tools.workspace.api.git.GitRepo
import java.util.concurrent.Future

class JiraExplorationGuidance(
    private val instanceTypes: List<InstanceType>,
    private val maxNodeCount: Int,
    private val minNodeCountForAvailability: Int,
    private val minApdexGain: Double,
    private val minThroughputGain: TemporalRate,
    private val db: InstanceType,
    private val resultsCache: HardwareExplorationResultCache
) : ExplorationGuidance {

    override fun space(): List<Hardware> = instanceTypes.flatMap { instanceType ->
        (6..maxNodeCount).map { Hardware(instanceType, it, db) }
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
        val smallerHardwareTests = (6 until hardware.nodeCount)
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
        task: TaskWorkspace,
        title: String
    ) = synchronized(this) {
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
        HardwareExplorationTable().summarize(
            results = sortedResults,
            table = task.isolateReport("exploration-table.csv")
        )
        HardwareExplorationChart(
            JiraInstanceTypeGrouping(compareBy { instanceTypes.indexOf(it) }),
            NodeCountXAxis(),
            GitRepo.findFromCurrentDirectory()
        ).plot(
            exploration = exploration,
            application = title,
            output = task.isolateReport("jira-exploration-chart.html")
        )
    }
}
