package com.atlassian.performance.tools.lib

import com.atlassian.performance.tools.report.api.StandardTimeline
import com.atlassian.performance.tools.report.api.result.EdibleResult
import com.atlassian.performance.tools.report.api.result.RawCohortResult
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.Matchers.everyItem
import org.hamcrest.Matchers.hasSize
import org.junit.Assert.assertThat
import org.junit.Test
import java.io.File
import java.time.Duration

class ApdexTest {

    @Test
    fun shouldScore() {
        val result = loadResult()

        val apdex = Apdex().score(result.actionMetrics)

        assertThat(apdex, equalTo(0.5796305541687469))
    }

    @Test
    fun shouldFindTheBiggestOffender() {
        val result = loadResult()

        val offenders = Apdex().findOffenders(result)

        assertThat(offenders.actionMetrics, hasSize(1291))
        assertThat(offenders.actionMetrics.map { it.label }, everyItem(equalTo("View Issue")))
    }

    private fun loadResult(): EdibleResult {
        return RawCohortResult.Factory().fullResult(
            cohort = "JIRA-JPTC-1339",
            results = File(javaClass.getResource("/JIRA-JPTC-1339").toURI()).toPath()
        )
            .prepareForJudgement(StandardTimeline(Duration.ofMinutes(20)))
    }
}