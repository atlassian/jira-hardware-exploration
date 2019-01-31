package com.atlassian.performance.tools.lib

import com.atlassian.performance.tools.report.api.StandardTimeline
import com.atlassian.performance.tools.report.api.result.RawCohortResult
import org.hamcrest.CoreMatchers.equalTo
import org.junit.Assert.assertThat
import org.junit.Test
import java.io.File
import java.time.Duration

class ApdexTest {

    @Test
    fun shouldScore() {
        val result = RawCohortResult.Factory().fullResult(
            cohort = "JIRA-JPTC-1339",
            results = File(javaClass.getResource("/JIRA-JPTC-1339").toURI()).toPath()
        )
            .prepareForJudgement(StandardTimeline(Duration.ofMinutes(20)))
            .actionMetrics

        val apdex = Apdex().score(result)

        assertThat(apdex, equalTo(0.5796305541687469))
    }
}