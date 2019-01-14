package com.atlassian.performance.tools.lib

import com.atlassian.performance.tools.jiraactions.api.parser.MergingActionMetricsParser
import com.atlassian.performance.tools.report.api.StandardTimeline
import com.atlassian.performance.tools.report.api.parser.MergingNodeCountParser
import com.atlassian.performance.tools.report.api.parser.SystemMetricsParser
import com.atlassian.performance.tools.report.api.result.FullCohortResult
import org.hamcrest.CoreMatchers.equalTo
import org.junit.Assert.assertThat
import org.junit.Test
import java.io.File
import java.time.Duration

class ApdexTest {

    @Test
    fun shouldScore() {
        val result = FullCohortResult(
            cohort = "JIRA-JPTC-1339",
            results = File(javaClass.getResource("/JIRA-JPTC-1339").toURI()).toPath(),
            actionParser = MergingActionMetricsParser(),
            nodeParser = MergingNodeCountParser(),
            systemParser = SystemMetricsParser()
        )
            .prepareForJudgement(StandardTimeline(Duration.ofMinutes(20)))
            .actionMetrics

        val apdex = Apdex().score(result)

        assertThat(apdex, equalTo(0.5796305541687469))
    }
}