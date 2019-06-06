package com.atlassian.performance.tools.lib

import com.atlassian.performance.tools.report.api.StandardTimeline
import com.atlassian.performance.tools.report.api.result.RawCohortResult
import org.hamcrest.CoreMatchers.equalTo
import org.junit.Assert.assertThat
import org.junit.Test
import java.io.File
import java.time.Duration

class WaterfallDrilldownTest {

    @Test
    fun shouldScore() {
        val result = RawCohortResult.Factory().fullResult(
            cohort = "large_c48_3nodes",
            results = File(javaClass.getResource("/large_c48_3nodes").toURI()).toPath()
        )
            .prepareForJudgement(StandardTimeline(Duration.ofMinutes(20)))
            .actionMetrics
        Waterfall().walk(result)

        val result2 = RawCohortResult.Factory().fullResult(
            cohort = "large_c59_3nodes",
            results = File(javaClass.getResource("/large_c59_3nodes").toURI()).toPath()
        )
            .prepareForJudgement(StandardTimeline(Duration.ofMinutes(20)))
            .actionMetrics
        Waterfall().walk(result2)

    }
}
