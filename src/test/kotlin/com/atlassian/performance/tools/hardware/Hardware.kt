package com.atlassian.performance.tools.hardware

import com.amazonaws.services.ec2.model.InstanceType
import com.atlassian.performance.tools.workspace.api.TaskWorkspace
import com.atlassian.performance.tools.workspace.api.TestWorkspace

internal data class Hardware(
    val instanceType: InstanceType,
    val nodeCount: Int
) {
    fun isolateRuns(
        workspace: TaskWorkspace
    ): TaskWorkspace = isolateSubTask(workspace, "runs")

    fun isolateSubTask(
        workspace: TaskWorkspace,
        subTask: String
    ): TaskWorkspace {
        return workspace
            .directory
            .resolve(instanceType.toString())
            .resolve("nodes")
            .resolve(nodeCount.toString())
            .resolve(subTask)
            .let { TaskWorkspace(it) }
    }

    fun nameCohort(
        workspace: TestWorkspace
    ): String {
        val run = workspace.directory.fileName.toString()
        return "$instanceType, $nodeCount nodes, run $run"
    }

    override fun toString(): String = "[$nodeCount * $instanceType]"
}
