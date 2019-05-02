package com.atlassian.performance.tools.hardware.guidance

import com.atlassian.performance.tools.hardware.Hardware
import com.atlassian.performance.tools.hardware.HardwareExplorationDecision
import com.atlassian.performance.tools.hardware.HardwareExplorationResult
import com.atlassian.performance.tools.workspace.api.TaskWorkspace
import java.util.concurrent.Future

internal class SkippedGuidance : ExplorationGuidance {

    override fun space(): List<Hardware> = emptyList()

    override fun decideTesting(
        hardware: Hardware,
        benchmark: (Hardware) -> Future<HardwareExplorationResult>
    ): HardwareExplorationDecision = HardwareExplorationDecision(
        hardware = hardware,
        worthExploring = false,
        reason = "Skipping exploration"
    )

    override fun report(
        exploration: List<HardwareExplorationResult>,
        task: TaskWorkspace,
        title: String
    ) {
    }
}
