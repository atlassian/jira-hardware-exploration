package com.atlassian.performance.tools.hardware

import com.atlassian.performance.tools.jiraactions.api.*
import com.atlassian.performance.tools.jirasoftwareactions.api.actions.ViewBacklogAction.Companion.VIEW_BACKLOG
import com.atlassian.performance.tools.lib.AccessLogThroughput
import com.atlassian.performance.tools.lib.Apdex
import com.atlassian.performance.tools.lib.ErrorGauge
import com.atlassian.performance.tools.lib.report.VirtualUsersPresenceJudge
import com.atlassian.performance.tools.report.api.StandardTimeline
import com.atlassian.performance.tools.report.api.result.EdibleResult
import com.atlassian.performance.tools.report.api.result.RawCohortResult

class HardwareMetric(
    private val scale: ApplicationScale,
    private val presenceJudge: VirtualUsersPresenceJudge
) {
    private val labels = listOf(
        VIEW_BACKLOG,
        VIEW_BOARD,
        VIEW_ISSUE,
        VIEW_DASHBOARD,
        SEARCH_WITH_JQL,
        ADD_COMMENT_SUBMIT,
        CREATE_ISSUE_SUBMIT,
        EDIT_ISSUE_SUBMIT,
        PROJECT_SUMMARY,
        BROWSE_PROJECTS,
        BROWSE_BOARDS
    ).map { it.label }

    fun score(
        hardware: Hardware,
        results: RawCohortResult
    ): HardwareTestResult {
        val postProcessedResult = postProcess(results)
        val metrics = postProcessedResult.actionMetrics.filter { it.label in labels }
        val apdex = Apdex().score(metrics)
        val throughput = AccessLogThroughput().gauge(results.results.toFile())
        val overallError = ErrorGauge().measureOverall(metrics)
        val maxActionError = ErrorGauge().measureMaxAction(metrics)
        return HardwareTestResult(
            hardware = hardware,
            apdex = apdex,
            apdexes = listOf(apdex),
            httpThroughput = throughput,
            httpThroughputs = listOf(throughput),
            results = listOf(results),
            overallError = overallError,
            overallErrors = listOf(overallError),
            maxActionError = maxActionError,
            maxActionErrors = listOf(maxActionError)
        )
    }

    /**
     * Currently post-processing is very memory-intensive,
     * so it needs to be sequential JVM-wide.
     */
    fun postProcess(
        rawResults: RawCohortResult
    ): EdibleResult = synchronized(POST_PROCESSING_LOCK) {
        val timeline = StandardTimeline(scale.load.total)
        val result = rawResults.prepareForJudgement(timeline)
        validate(result)
        return result
    }

    private fun validate(
        result: EdibleResult
    ) {
        if (result.failure != null) {
            throw Exception("${result.cohort} failed", result.failure)
        }
        val vuNodes = scale.vuNodes
        val roundedExpectedVus = (scale.load.virtualUsers / vuNodes) * vuNodes
        presenceJudge.judge(
            result = result,
            expectedVus = roundedExpectedVus
        )
    }

    private companion object {
        private val POST_PROCESSING_LOCK = Object()
    }
}
