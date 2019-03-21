package com.atlassian.performance.tools.hardware

import com.amazonaws.services.ec2.model.InstanceType
import com.atlassian.performance.tools.workspace.api.TaskWorkspace
import com.atlassian.performance.tools.workspace.api.TestWorkspace

data class Hardware(
    val jira: InstanceType,
    val nodeCount: Int,
    val db: InstanceType
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
            .resolve(jira.toString())
            .resolve("nodes")
            .resolve(nodeCount.toString())
            .resolve("dbs")
            .resolve(db.toString())
            .resolve(subTask)
            .let { TaskWorkspace(it) }
    }

    fun legacyNameCohort(
        workspace: TestWorkspace
    ): String {
        val run = workspace.directory.fileName.toString()
        return "$jira, $nodeCount nodes, run $run"
    }

    fun nameCohort(
        workspace: TestWorkspace
    ): String {
        val run = workspace.directory.fileName.toString()
        return "$nodeCount x $jira Jira, $db DB, run $run"
    }

    override fun toString(): String = "[$nodeCount x $jira Jira, $db DB]"
}
