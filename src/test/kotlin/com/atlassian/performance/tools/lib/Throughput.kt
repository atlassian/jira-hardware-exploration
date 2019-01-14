package com.atlassian.performance.tools.lib

import java.time.Duration

class Throughput(
    val count: Double,
    val period: Duration
) {
    operator fun plus(
        other: Throughput
    ): Throughput {
        if (period == other.period) {
            return Throughput(
                count = count + other.count,
                period = period
            )
        } else {
            throw Exception("We're not able to translate different throughput periods yet")
        }
    }

    fun scalePeriod(
        newPeriod: Duration
    ): Throughput {
        val factor = newPeriod.toMillis().toDouble() / period.toMillis().toDouble()
        return Throughput(
            count = count * factor,
            period = newPeriod
        )
    }

    override fun toString(): String = "$count per $period"

    companion object {
        val ZERO = Throughput(0.0, Duration.ofSeconds(1))
    }
}