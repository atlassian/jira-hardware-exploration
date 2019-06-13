package com.atlassian.performance.tools.hardware.guidance

import com.atlassian.performance.tools.hardware.Hardware
import com.atlassian.performance.tools.hardware.HardwareExplorationDecision
import com.atlassian.performance.tools.hardware.HardwareExplorationResult
import com.atlassian.performance.tools.hardware.HardwareExplorationResultCache
import com.atlassian.performance.tools.hardware.report.HardwareExplorationTable
import com.atlassian.performance.tools.workspace.api.TaskWorkspace
import java.util.concurrent.Future

class SingleHardwareGuidance(private val hardware: Hardware) : ExplorationGuidance {

    override fun space(): List<Hardware> {
        return listOf(hardware)
    }

    override fun decideTesting(
        hardware: Hardware,
        benchmark: (Hardware) -> Future<HardwareExplorationResult>
    ): HardwareExplorationDecision = HardwareExplorationDecision(
        hardware = hardware,
        worthExploring = true,
        reason = "Testing single hardware"
    )

    override fun report(
        exploration: List<HardwareExplorationResult>,
        task: TaskWorkspace,
        title: String,
        resultsCache: HardwareExplorationResultCache
    ) = synchronized(this) {
        HardwareExplorationTable().summarize(
            results = exploration,
            table = task.isolateReport("single-hardware-table.csv")
        )
    }
}
