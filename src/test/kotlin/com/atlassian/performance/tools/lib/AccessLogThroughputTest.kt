package com.atlassian.performance.tools.lib

import org.hamcrest.CoreMatchers.equalTo
import org.junit.Assert.assertThat
import org.junit.Test
import java.io.File
import java.time.Duration

class AccessLogThroughputTest {

    @Test
    fun shouldGauge() {
        val rawResults = File(javaClass.getResource("/JIRA-JPTC-1339").toURI())

        val throughput = AccessLogThroughput().gauge(rawResults)

        assertThat(throughput.change, equalTo(41.70824779594451))
        assertThat(throughput.time, equalTo(Duration.ofSeconds(1)))
    }

    @Test
    fun shouldGaugeWithSquareBracketsInUri() {
        val rawResults = File(javaClass.getResource("/QUICK-8").toURI())

        val throughput = AccessLogThroughput().gauge(rawResults)

        assertThat(throughput.change, equalTo(9.626355296080066))
        assertThat(throughput.time, equalTo(Duration.ofSeconds(1)))
    }

    @Test
    fun shouldGaugeOvernightLogs() {
        val rawResults = File(javaClass.getResource("/QUICK-73").toURI())

        AccessLogThroughput().gauge(rawResults)
    }
}
