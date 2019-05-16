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
     * [On-Demand pricing](https://aws.amazon.com/ec2/pricing/on-demand/) as of 2019-05-16.
     * We can use https://aws.amazon.com/blogs/aws/new-aws-price-list-api/ in the future.
     */
    private fun estimateCost(
        instanceType: InstanceType
    ): TemporalRate {
        val usdPerHour = when (instanceType) {
            C48xlarge -> 1.811
            C5Large -> 0.096
            C5Xlarge -> 0.192
            C52xlarge -> 0.384
            C54xlarge -> 0.768
            C59xlarge -> 1.728
            C518xlarge -> 3.924
            M4Large -> 0.111
            M4Xlarge -> 0.222
            M42xlarge -> 0.444
            M44xlarge -> 0.888
            M410xlarge -> 2.220
            M416xlarge -> 3.552
            else -> throw Exception("Don't know how to estimate the cost of $instanceType")
        }
        return TemporalRate(usdPerHour, Duration.ofHours(1))
    }

    override fun toString(): String = "[$nodeCount x $jira Jira, $db DB]"
}
