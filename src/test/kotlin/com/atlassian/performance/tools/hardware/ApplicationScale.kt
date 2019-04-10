package com.atlassian.performance.tools.hardware

import com.amazonaws.regions.Regions.EU_WEST_1
import com.atlassian.performance.tools.aws.api.StorageLocation
import com.atlassian.performance.tools.awsinfrastructure.S3DatasetPackage
import com.atlassian.performance.tools.awsinfrastructure.api.DatasetCatalogue
import com.atlassian.performance.tools.infrastructure.api.database.PostgresDatabase
import com.atlassian.performance.tools.infrastructure.api.dataset.Dataset
import com.atlassian.performance.tools.infrastructure.api.jira.JiraHomePackage
import com.atlassian.performance.tools.lib.LicenseOverridingDatabase
import com.atlassian.performance.tools.lib.overrideDatabase
import com.atlassian.performance.tools.lib.toExistingFile
import com.atlassian.performance.tools.virtualusers.api.TemporalRate
import com.atlassian.performance.tools.virtualusers.api.VirtualUserLoad
import java.net.URI
import java.nio.file.Paths
import java.time.Duration

class ApplicationScale(
    val description: String,
    val dataset: Dataset,
    val load: VirtualUserLoad,
    val vuNodes: Int
)

private val JIRA_XL_DATASET = StorageLocation(
    uri = URI("s3://jpt-custom-postgres-xl/dataset-7m"),
    region = EU_WEST_1
).let { location ->
    Dataset(
        label = "7M issues",
        database = PostgresDatabase(
            source = S3DatasetPackage(
                artifactName = "database.tar.bz2",
                location = location,
                unpackedPath = "database",
                downloadTimeout = Duration.ofMinutes(40)
            ),
            dbName = "atldb",
            dbUser = "postgres",
            dbPassword = "postgres"
        ),
        jiraHomeSource = JiraHomePackage(
            S3DatasetPackage(
                artifactName = "jirahome.tar.bz2",
                location = location,
                unpackedPath = "jirahome",
                downloadTimeout = Duration.ofMinutes(40)
            )
        )
    )
}.overrideDatabase { overrideLicense(it) }

private val JIRA_L_DATASET = DatasetCatalogue().custom(
    location = StorageLocation(
        uri = URI("s3://jpt-custom-datasets-storage-a008820-datasetbucket-1sjxdtrv5hdhj/")
            .resolve("a12fc4c5-3973-41f0-bf56-ede393677028"),
        region = EU_WEST_1
    ),
    label = "1M issues",
    databaseDownload = Duration.ofMinutes(20),
    jiraHomeDownload = Duration.ofMinutes(20)
).overrideDatabase { overrideLicense(it) }

private fun overrideLicense(
    dataset: Dataset
): LicenseOverridingDatabase {
    val localLicense = Paths.get("jira-license.txt")
    return LicenseOverridingDatabase(
        dataset.database,
        listOf(
            localLicense
                .toExistingFile()
                ?.readText()
                ?: throw Exception("Put a Jira license to ${localLicense.toAbsolutePath()}")
        ))
}

val JIRA_EXTRA_LARGE = ApplicationScale(
    description = "Jira XL profile",
    dataset = JIRA_XL_DATASET,
    load = VirtualUserLoad.Builder()
        .virtualUsers(150)
        .ramp(Duration.ofSeconds(90))
        .flat(Duration.ofMinutes(20))
        .maxOverallLoad(TemporalRate(30.0, Duration.ofSeconds(1)))
        .build(),
    vuNodes = 12
)

val JIRA_LARGE = ApplicationScale(
    description = "Jira L profile",
    dataset = JIRA_L_DATASET,
    load = VirtualUserLoad.Builder()
        .virtualUsers(75)
        .ramp(Duration.ofSeconds(90))
        .flat(Duration.ofMinutes(20))
        .maxOverallLoad(TemporalRate(15.0, Duration.ofSeconds(1)))
        .build(),
    vuNodes = 6
)