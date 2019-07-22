package com.atlassian.performance.tools.lib

import com.atlassian.performance.tools.jiraactions.api.parser.ActionMetricsParser
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.io.File
import java.nio.file.Paths

class ErrorRateTest {

    @Test
    fun shouldMeasure() {
        val path = Paths.get("/QUICK-132-fix-v3/c5.9xlarge/nodes/3/dbs/m4.4xlarge/runs/5/")
            .resolve("3 x c5.9xlarge Jira, m4.4xlarge DB, run 5/virtual-users/virtual-user-node-10/test-results/")
            .resolve("050f54b3-2f92-4480-8bdb-b8079e3ab5b8/action-metrics.jpt")
        val metrics = File(javaClass.getResource(path.toString()).toURI()).inputStream().use {
            ActionMetricsParser().parse(it)
        }

        val errorRate = ErrorRate().measure(metrics)

        assertThat(errorRate).isEqualTo(0.09375)
    }
}
