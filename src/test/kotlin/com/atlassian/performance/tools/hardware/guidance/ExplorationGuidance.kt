package com.atlassian.performance.tools.hardware.guidance

import com.atlassian.performance.tools.hardware.*
import com.atlassian.performance.tools.workspace.api.TaskWorkspace
import java.util.concurrent.Future

interface ExplorationGuidance {
    fun space(): List<Hardware>

    fun decideTesting(
        hardware: Hardware,
        benchmark: (Hardware) -> Future<HardwareExplorationResult>
    ): HardwareExplorationDecision

    fun report(
        exploration: List<HardwareExplorationResult>,
        requirements: OutcomeRequirements,
        task: TaskWorkspace,
        title: String,
        resultsCache: HardwareExplorationResultCache
    )
}
