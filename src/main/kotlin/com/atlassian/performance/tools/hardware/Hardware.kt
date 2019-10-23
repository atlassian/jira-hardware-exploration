package com.atlassian.performance.tools.hardware

import com.amazonaws.services.ec2.model.InstanceType
import com.amazonaws.services.ec2.model.InstanceType.*
import com.atlassian.performance.tools.lib.plus
import com.atlassian.performance.tools.virtualusers.api.TemporalRate
import com.atlassian.performance.tools.workspace.api.TaskWorkspace
import com.atlassian.performance.tools.workspace.api.TestWorkspace
import java.time.Duration

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

    fun estimateCost(): TemporalRate {
        val sharedHomeNodes = 1
        val jiraLikeNodes = nodeCount + sharedHomeNodes
        val dbLikeNodes = 1
        val jiraCost = estimateCost(jira).times(jiraLikeNodes.toDouble())
        val dbCost = estimateCost(db).times(dbLikeNodes.toDouble())
        return jiraCost + dbCost
    }

    /**
     * [On-Demand pricing](https://aws.amazon.com/ec2/pricing/on-demand/) as of 2019-09-05 for the Ohio (eu-east-2).
     * We can use https://aws.amazon.com/blogs/aws/new-aws-price-list-api/ in the future.
     */
    private fun estimateCost(
        instanceType: InstanceType
    ): TemporalRate {
        val usdPerHour = when (instanceType) {
            C48xlarge -> 1.591
            C5Large -> 0.085
            C5Xlarge -> 0.170
            C52xlarge -> 0.340
            C54xlarge -> 0.680
            C59xlarge -> 1.530
            C518xlarge -> 3.060
            M4Large -> 0.1
            M4Xlarge -> 0.2
            M42xlarge -> 0.4
            M44xlarge -> 0.8
            M410xlarge -> 2.0
            M416xlarge -> 3.2
            else -> throw Exception("Don't know how to estimate the cost of $instanceType")
        }
        return TemporalRate(usdPerHour, Duration.ofHours(1))
    }

    override fun toString(): String = "[$nodeCount x $jira Jira, $db DB]"
}
