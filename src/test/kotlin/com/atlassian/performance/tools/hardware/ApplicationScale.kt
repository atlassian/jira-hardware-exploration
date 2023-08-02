package com.atlassian.performance.tools.hardware

import com.atlassian.performance.tools.lib.infrastructure.AdminDataset
import com.atlassian.performance.tools.virtualusers.api.TemporalRate
import com.atlassian.performance.tools.virtualusers.api.VirtualUserLoad
import java.time.Duration

class ApplicationScale(
    val description: String,
    val cacheKey: String,
    val dataset: AdminDataset,
    val load: VirtualUserLoad,
    val vuNodes: Int
)

class ApplicationScales {
    private val datasets = HwrDatasetCatalogue()

    fun extraLarge(
        jiraVersion: String,
        postgres: Boolean
    ): ApplicationScale {
        val major = jiraVersion.major()
        val jira8 = major == 8
        return ApplicationScale(
            description = "Jira $jiraVersion XL",
            cacheKey = "xl-jsw-$jiraVersion",
            dataset = when {
                jira8 && !postgres -> datasets.xl8Mysql()
                !jira8 && !postgres -> datasets.xl7Mysql()
                else -> throw Exception("We don't have an XL dataset matching jira8=$jira8 and postgres=$postgres")
            },
            load = VirtualUserLoad.Builder()
                .virtualUsers(150)
                .ramp(Duration.ofMinutes(2))
                .flat(Duration.ofMinutes(20))
                .maxOverallLoad(TemporalRate(30.0, Duration.ofSeconds(1)))
                .build(),
            vuNodes = 12
        )
    }

    fun large(
        jiraVersion: String
    ): ApplicationScale {
        return ApplicationScale(
            description = "Jira $jiraVersion L",
            cacheKey = "l-jsw-$jiraVersion",
            dataset = when (jiraVersion.major()) {
                9 -> datasets.l9Mysql()
                8 -> datasets.l8Mysql()
                else -> datasets.l7Mysql()
            },
            load = VirtualUserLoad.Builder()
                .virtualUsers(75)
                .ramp(Duration.ofMinutes(2))
                .flat(Duration.ofMinutes(20))
                .maxOverallLoad(TemporalRate(15.0, Duration.ofSeconds(1)))
                .build(),
            vuNodes = 6
        )
    }

    private fun String.major(): Int {
        return this
            .split(".")
            .first()
            .toInt()
    }
}
