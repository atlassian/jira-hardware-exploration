package com.atlassian.performance.tools.lib.chart

import java.math.BigDecimal

internal interface Point<X> {

    val x: X
    val y: BigDecimal
    fun labelX(): String
}