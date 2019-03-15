package com.atlassian.performance.tools.hardware

import com.amazonaws.regions.Regions
import com.atlassian.performance.tools.aws.api.StorageLocation
import com.atlassian.performance.tools.awsinfrastructure.api.DatasetCatalogue
import com.atlassian.performance.tools.infrastructure.api.database.MySqlDatabase
import com.atlassian.performance.tools.infrastructure.api.dataset.Dataset
import com.atlassian.performance.tools.infrastructure.api.dataset.HttpDatasetPackage
import com.atlassian.performance.tools.infrastructure.api.jira.JiraHomePackage
import com.atlassian.performance.tools.lib.LicenseOverridingDatabase
import com.atlassian.performance.tools.lib.overrideDatabase
import com.atlassian.performance.tools.lib.toExistingFile
import java.net.URI
import java.nio.file.Paths
import java.time.Duration.ofMinutes

class MyDatasetCatalogue {

    fun oneMillionIssues() = DatasetCatalogue().custom(
        location = StorageLocation(
            uri = URI("s3://jpt-custom-datasets-storage-a008820-datasetbucket-1sjxdtrv5hdhj/")
                .resolve("a12fc4c5-3973-41f0-bf56-ede393677028"),
            region = Regions.EU_WEST_1
        ),
        label = "1M issues",
        databaseDownload = ofMinutes(20),
        jiraHomeDownload = ofMinutes(20)
    ).overrideDatabase { overrideLicense(it) }

    fun sevenThousandIssues(): Dataset = DatasetCatalogue().custom(
        location = StorageLocation(
            URI("s3://jpt-custom-datasets-storage-a008820-datasetbucket-1sjxdtrv5hdhj/")
                .resolve("af4c7d3b-925c-464c-ab13-79f615158316"),
            Regions.EU_WEST_1
        ),
        label = "7k issues",
        databaseDownload = ofMinutes(5),
        jiraHomeDownload = ofMinutes(5)
    ).overrideDatabase { overrideLicense(it) }

    fun hundredThousandUsers(): Dataset = URI("https://s3-eu-central-1.amazonaws.com/")
        .resolve("jpt-custom-datasets-storage-a008820-datasetbucket-dah44h6l1l8p/")
        .resolve("jsw-7.13.0-100k-users-sync/")
        .let {
            Dataset(
                label = "100k users",
                database = MySqlDatabase(HttpDatasetPackage(
                    uri = it.resolve("database.tar.bz2"),
                    downloadTimeout = ofMinutes(3)
                )),
                jiraHomeSource = JiraHomePackage(
                    HttpDatasetPackage(
                        uri = it.resolve("jirahome.tar.bz2"),
                        downloadTimeout = ofMinutes(3)
                    )
                )
            )
        }
        .overrideDatabase { overrideLicense(it) }

    private fun overrideLicense(
        originalDataset: Dataset
    ): LicenseOverridingDatabase {
        val localLicense = Paths.get("jira-license.txt")
        return LicenseOverridingDatabase(
            originalDataset.database,
            listOf(
                localLicense
                    .toExistingFile()
                    ?.readText()
                    ?: throw  Exception("Put a Jira license to ${localLicense.toAbsolutePath()}")
            ))
    }
}
