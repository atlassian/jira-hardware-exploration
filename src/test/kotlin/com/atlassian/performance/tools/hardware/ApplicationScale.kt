package com.atlassian.performance.tools.hardware

import com.atlassian.performance.tools.infrastructure.api.dataset.Dataset
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
    private val extraLargeVuNodes = 12;
    private val largeVuNodes = 6;

    fun extraLargeDataExtraLargeLoad(
        jiraVersion: String,
        postgres: Boolean
    ): ApplicationScale {
        val jira8 = isJira8(jiraVersion)
        return ApplicationScale(
            description = "Jira $jiraVersion XL",
            cacheKey = "xl-jsw-$jiraVersion",
            dataset = when {
                jira8 && !postgres -> datasets.xl8Mysql()
                !jira8 && !postgres -> datasets.xl7Mysql()
                else -> throw Exception("We don't have an XL dataset matching jira8=$jira8 and postgres=$postgres")
            },
            // XL load + VU Nodes
            load = extraLargeLoad(),
            vuNodes = extraLargeVuNodes
        )
    }

    fun extraLargeDateLargeLoad(
        jiraVersion: String,
        postgres: Boolean
    ): ApplicationScale {
        val jira8 = isJira8(jiraVersion)
        return ApplicationScale(
            description = "Jira $jiraVersion XL",
            cacheKey = "xl-jsw-$jiraVersion",
            dataset = when {
                jira8 && !postgres -> datasets.xl8Mysql()
                !jira8 && !postgres -> datasets.xl7Mysql()
                else -> throw Exception("We don't have an XL dataset matching jira8=$jira8 and postgres=$postgres")
            },
            // L load + VU Nodes
            load = largeLoad(),
            vuNodes = largeVuNodes
        )
    }

    fun largeDataLargeLoad(
        jiraVersion: String
    ): ApplicationScale {
        val jira8 = isJira8(jiraVersion)
        return ApplicationScale(
            description = "Jira $jiraVersion L",
            cacheKey = "l-jsw-$jiraVersion",
            dataset = when {
                jira8 -> datasets.l8Mysql()
                else -> datasets.l7Mysql()
            },
            // L load + VU Nodes
            load = largeLoad(),
            vuNodes = largeVuNodes
        )
    }

    fun largeDataExtraLargeLoad(
        jiraVersion: String
    ): ApplicationScale {
        val jira8 = isJira8(jiraVersion)
        return ApplicationScale(
            description = "Jira $jiraVersion L",
            cacheKey = "l-jsw-$jiraVersion",
            dataset = when {
                jira8 -> datasets.l8Mysql()
                else -> datasets.l7Mysql()
            },
            // XL load + VU Nodes
            load = extraLargeLoad(),
            vuNodes = extraLargeVuNodes
        )
    }

    private fun largeLoad(): VirtualUserLoad {
        return VirtualUserLoad.Builder()
            .virtualUsers(75)
            .ramp(Duration.ofMinutes(2))
            .flat(Duration.ofMinutes(20))
            .maxOverallLoad(TemporalRate(15.0, Duration.ofSeconds(1)))
            .build()
    }

    private fun extraLargeLoad(): VirtualUserLoad {
        return VirtualUserLoad.Builder()
            .virtualUsers(150)
            .ramp(Duration.ofMinutes(2))
            .flat(Duration.ofMinutes(20))
            .maxOverallLoad(TemporalRate(30.0, Duration.ofSeconds(1)))
            .build()
    }

    private fun isJira8(jiraVersion: String): Boolean {
        return jiraVersion
            .split(".")
            .first()
            .toInt() >= 8
    }
}
