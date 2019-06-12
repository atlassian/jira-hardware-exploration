package com.atlassian.performance.tools.lib

import com.atlassian.performance.tools.report.api.StandardTimeline
import com.atlassian.performance.tools.report.api.result.RawCohortResult
import org.hamcrest.CoreMatchers.equalTo
import org.junit.Assert.assertThat
import org.junit.Test
import java.io.File
import java.nio.file.Paths
import java.time.Duration

class WaterfallDrilldownTest {

    @Test
    fun shouldAnalysis() {

        mapOf(
            "large_c48_3nodes" to File(javaClass.getResource("/large_c48_3nodes").toURI()).toPath(),
            "large_c59_3nodes" to File(javaClass.getResource("/large_c59_3nodes").toURI()).toPath()
        ).forEach() { (cohort, results) ->
            run {
                val result = RawCohortResult.Factory().fullResult(
                    cohort = cohort,
                    results = results
                ).prepareForJudgement(StandardTimeline(Duration.ofMinutes(20)))
                    .actionMetrics
                println("\nProcessing $cohort")
                Waterfall().walk(result)
            }
        }
    }
}
