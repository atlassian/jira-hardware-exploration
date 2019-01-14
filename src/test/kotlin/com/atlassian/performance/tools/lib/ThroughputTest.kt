package com.atlassian.performance.tools.lib

import org.hamcrest.CoreMatchers.equalTo
import org.junit.Assert.assertThat
import org.junit.Test
import java.time.Duration.*

class ThroughputTest {

    @Test
    fun shouldScaleDown() {
        val original = Throughput(240.0, ofMinutes(1))

        val scaled = original.scalePeriod(ofSeconds(1))

        assertThat(scaled.count, equalTo(4.0))
        assertThat(scaled.period, equalTo(ofSeconds(1)))
    }

    @Test
    fun shouldScaleUp() {
        val original = Throughput(2.0, ofMinutes(1))

        val scaled = original.scalePeriod(ofHours(1))

        assertThat(scaled.count, equalTo(120.0))
        assertThat(scaled.period, equalTo(ofHours(1)))
    }

    @Test
    fun shouldScaleWithNonUnitPeriod() {
        val original = Throughput(100.0, ofSeconds(1))

        val scaled = original.scalePeriod(ofSeconds(3))

        assertThat(scaled.count, equalTo(300.0))
        assertThat(scaled.period, equalTo(ofSeconds(3)))
    }

    @Test
    fun shouldAdd() {
        val alpha = Throughput(3.0, ofSeconds(1))
        val beta = Throughput(5.0, ofSeconds(1))

        val sum = alpha + beta

        assertThat(sum.count, equalTo(8.0))
        assertThat(sum.period, equalTo(ofSeconds(1)))
    }
}