package com.atlassian.performance.tools.hardware

import com.atlassian.performance.tools.lib.infrastructure.AdminDataset
import com.atlassian.performance.tools.virtualusers.api.TemporalRate
import com.atlassian.performance.tools.virtualusers.api.VirtualUserLoad
import java.time.Duration

class ApplicationScale(
    val description: String,
    val dataset: AdminDataset,
    val load: VirtualUserLoad,
    val vuNodes: Int
)

class ApplicationScales {
    private val datasets = HwrDatasetCatalogue()

    fun extraLarge(
        jiraVersion: String
    ): ApplicationScale {
        val jira8 = isJira8(jiraVersion)
        return ApplicationScale(
            description = "Jira XL profile",
            dataset = if (jira8) datasets.xl8Mysql() else datasets.xl7Mysql(),
            load = VirtualUserLoad.Builder()
                .virtualUsers(900)
                .ramp(Duration.ofSeconds(90))
                .flat(Duration.ofMinutes(20))
                .maxOverallLoad(TemporalRate(10000.0, Duration.ofSeconds(1)))
                .build(),
            vuNodes = 72
        )
    }

    fun large(
        jiraVersion: String
    ): ApplicationScale {
        val jira8 = isJira8(jiraVersion)
        return ApplicationScale(
            description = "Jira L profile",
            dataset = when {
                jira8 -> datasets.l8Mysql()
                else -> datasets.l7Mysql()
            },
            load = VirtualUserLoad.Builder()
                .virtualUsers(75)
                .ramp(Duration.ofSeconds(90))
                .flat(Duration.ofMinutes(20))
                .maxOverallLoad(TemporalRate(15.0, Duration.ofSeconds(1)))
                .build(),
            vuNodes = 6
        )
    }

    private fun isJira8(jiraVersion: String): Boolean {
        return jiraVersion
            .split(".")
            .first()
            .toInt() >= 8
    }
}
