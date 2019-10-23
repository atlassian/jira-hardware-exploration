package com.atlassian.performance.tools.lib

import com.atlassian.performance.tools.virtualusers.api.TemporalRate
import java.time.Duration

operator fun TemporalRate.plus(
    other: TemporalRate
): TemporalRate {
    val shorterTime = minOf(this.time, other.time)
    val scaledThis = this.scaleTime(shorterTime)
    val scaledOther = other.scaleTime(shorterTime)
    return TemporalRate(
        scaledThis.change + scaledOther.change,
        shorterTime
    )
}

operator fun TemporalRate.minus(
    other: TemporalRate
): TemporalRate {
    val shorterTime = minOf(this.time, other.time)
    val scaledThis = this.scaleTime(shorterTime)
    val scaledOther = other.scaleTime(shorterTime)
    return TemporalRate(
        scaledThis.change - scaledOther.change,
        shorterTime
    )
}

fun TemporalRate.invert(): TemporalRate {
    return TemporalRate(
        1.0 / change,
        time
    )
}

val ZERO_RATE = TemporalRate(0.0, Duration.ofSeconds(1))
