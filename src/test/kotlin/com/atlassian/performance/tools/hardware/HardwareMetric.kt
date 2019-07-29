package com.atlassian.performance.tools.hardware

import com.atlassian.performance.tools.jiraactions.api.*
import com.atlassian.performance.tools.jirasoftwareactions.api.actions.ViewBacklogAction.Companion.VIEW_BACKLOG
import com.atlassian.performance.tools.lib.AccessLogThroughput
import com.atlassian.performance.tools.lib.Apdex
import com.atlassian.performance.tools.lib.ErrorRate
import com.atlassian.performance.tools.report.api.Timeline
import com.atlassian.performance.tools.report.api.result.EdibleResult
import com.atlassian.performance.tools.report.api.result.RawCohortResult
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

class HardwareMetric(
    private val timeline: Timeline,
    private val errorRateWarningThreshold: Double
) {
    private val logger: Logger = LogManager.getLogger(this::class.java)

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
        val cohort = postProcessedResult.cohort
        if (postProcessedResult.failure != null) {
            throw Exception("$cohort failed", postProcessedResult.failure)
        }
        val metrics = postProcessedResult.actionMetrics.filter { it.label in labels }
        val apdex = Apdex().score(metrics)
        val throughput = AccessLogThroughput().gauge(results.results.toFile())
        val errorRate = ErrorRate().measure(metrics)
        val hardwareResult = HardwareTestResult(
            hardware = hardware,
            apdex = apdex,
            apdexes = listOf(apdex),
            httpThroughput = throughput,
            httpThroughputs = listOf(throughput),
            results = listOf(results),
            errorRate = errorRate,
            errorRates = listOf(errorRate)
        )
        if (hardwareResult.errorRate > errorRateWarningThreshold) {
            logger.warn("Error rate for $cohort is too high: $errorRate")
        }
        return hardwareResult
    }

    /**
     * Currently post-processing is very memory-intensive,
     * so it needs to be sequential JVM-wide.
     */
    fun postProcess(
        rawResults: RawCohortResult
    ): EdibleResult = synchronized(POST_PROCESSING_LOCK) {
        return rawResults.prepareForJudgement(timeline)
    }

    private companion object {
        private val POST_PROCESSING_LOCK = Object()
    }
}
