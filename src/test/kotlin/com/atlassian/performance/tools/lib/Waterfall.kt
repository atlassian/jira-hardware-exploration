package com.atlassian.performance.tools.lib

import com.atlassian.performance.tools.jiraactions.api.ActionMetric
import com.atlassian.performance.tools.jiraactions.api.ActionResult
import com.atlassian.performance.tools.jiraactions.api.ActionType
import java.time.Duration

class Waterfall {
    private val satisfactoryThreshold = Duration.ofSeconds(1)
    private val tolerableThreshold = Duration.ofSeconds(4)

    fun walk(
        metrics: List<ActionMetric>
    ) {
        val metricsFiltered = metrics.filter { matches(it) }
            .apply {
                println("Sample : ${size}")
            }
        walkRawResult(metricsFiltered)
        walkGap(metricsFiltered)
        //diffDrilldown(metrics)
    }

    fun walkRawResult(
        metrics: List<ActionMetric>
    ) {
        metrics.filter { matches(it) }
            .map {
                it.duration.toMillis()
            }.apply {
                println("Duration Average/Median: ${this.average().toLong()}/${median(this)}")
            }

        metrics.filter { matches(it) }
            .map {
                it.drilldown!!.navigations.first().loadEventEnd.toMillis()
            }.apply {
                println("loadEventEnd Average/Median: ${this.average().toLong()}/${median(this)}")
            }

    }

    fun walkGap(
        metrics: List<ActionMetric>
    ) {
        metrics.filter { matches(it) }
            .map {
                (it.duration - it.drilldown!!.navigations.first().loadEventEnd).toMillis()
            }.apply {
                println("Gap Average/Median: ${this.average().toLong()}/${median(this)}")
            }

        metrics.filter { matches(it) }
            .map {
                (it.duration - it.drilldown!!.navigations.first().loadEventEnd).toMillis()
            }.filter {
                it > 500
            }.let {
                println("Number of Sample with Gap > 500: ${it.size}")
            }
    }

    private fun diffDrilldown(metrics: List<ActionMetric>) {
        println("Short Gap req:")
        metrics.filter { matches(it) }
            .filter {
                ((it.duration - it.drilldown!!.navigations.first().loadEventEnd).toMillis() < 200
                    && it.drilldown != null)
            }.last().let {
                it.drilldown!!.navigations.forEach {
                    println("Started ${it.resource.entry.startTime} for ${it.resource.entry.duration.toMillis()} : ${it.resource.entry.name}")
                }
                it.drilldown!!.resources.forEach {
                    println("Started ${it.entry.startTime} for ${it.entry.duration.toMillis()} : ${it.entry.name}")
                }
            }

        println("Long Gap req:")
        metrics.filter { matches(it) }
            .filter {
                ((it.duration - it.drilldown!!.navigations.first().loadEventEnd).toMillis() > 500
                    && it.drilldown != null)
            }.last().let {
                it.drilldown!!.navigations.forEach {
                    println("Started ${it.resource.entry.startTime} for ${it.resource.entry.duration.toMillis()} : ${it.resource.entry.name}")
                }
                it.drilldown!!.resources.forEach {
                    println("Started ${it.entry.startTime} for ${it.entry.duration.toMillis()} : ${it.entry.name}")
                }
            }
    }

    fun median(list: List<Long>): Long = list.sorted().let {
        it[it.size / 2] + it[(it.size - 1) / 2] / 2
    }

    private fun matches(
        metric: ActionMetric
    ): Boolean = when {
        (metric.result == ActionResult.OK)
            && (metric.label == "View Issue") -> true
//            && (metric.duration - metric.drilldown!!.navigations.first().loadEventEnd).toMillis() > 300 -> true
        else -> false
    }
}
