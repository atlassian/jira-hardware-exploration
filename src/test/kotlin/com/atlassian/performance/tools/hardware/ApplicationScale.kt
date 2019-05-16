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

private val DATASETS = HwrDatasetCatalogue()

fun extraLarge(
    jira8: Boolean,
    postgres: Boolean
): ApplicationScale = ApplicationScale(
    description = "Jira XL profile",
    dataset = when {
        jira8 && postgres -> DATASETS.xl8Postgres()
        jira8 && !postgres -> DATASETS.xl8Mysql()
        !jira8 && !postgres -> DATASETS.xl7Mysql()
        else -> throw Exception("We don't have an XL dataset matching jira8=$jira8 and postgres=$postgres")
    },
    load = VirtualUserLoad.Builder()
        .virtualUsers(150)
        .ramp(Duration.ofSeconds(90))
        .flat(Duration.ofMinutes(90))
        .maxOverallLoad(TemporalRate(30.0, Duration.ofSeconds(1)))
        .build(),
    vuNodes = 12
)

fun large(
    jira8: Boolean,
    postgres: Boolean
) = ApplicationScale(
    description = "Jira L profile",
    dataset = when {
        jira8 && !postgres -> DATASETS.l8Mysql()
        !jira8 && !postgres -> DATASETS.l7Mysql()
        else -> throw Exception("We don't have an L dataset matching jira8=$jira8 and postgres=$postgres")
    },
    load = VirtualUserLoad.Builder()
        .virtualUsers(75)
        .ramp(Duration.ofSeconds(90))
        .flat(Duration.ofMinutes(20))
        .maxOverallLoad(TemporalRate(15.0, Duration.ofSeconds(1)))
        .build(),
    vuNodes = 6
)
