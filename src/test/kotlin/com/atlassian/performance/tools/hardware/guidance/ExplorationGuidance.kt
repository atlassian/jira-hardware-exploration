package com.atlassian.performance.tools.hardware.guidance

import com.atlassian.performance.tools.hardware.Hardware
import com.atlassian.performance.tools.hardware.HardwareExplorationDecision
import com.atlassian.performance.tools.hardware.HardwareExplorationResult
import com.atlassian.performance.tools.workspace.api.TaskWorkspace
import java.util.concurrent.Future

interface ExplorationGuidance {
    fun space(): List<Hardware>

    fun decideTesting(
        hardware: Hardware,
        benchmark: (Hardware) -> Future<HardwareExplorationResult>
    ): HardwareExplorationDecision

    fun report(
        results: List<HardwareExplorationResult>,
        task: TaskWorkspace,
        title: String
    )
}
