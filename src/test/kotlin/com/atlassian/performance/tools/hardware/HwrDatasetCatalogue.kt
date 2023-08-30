package com.atlassian.performance.tools.hardware

import com.amazonaws.regions.Regions
import com.atlassian.performance.tools.aws.api.StorageLocation
import com.atlassian.performance.tools.awsinfrastructure.api.DatasetCatalogue
import com.atlassian.performance.tools.infrastructure.api.database.Database
import com.atlassian.performance.tools.infrastructure.api.database.LicenseOverridingMysql
import com.atlassian.performance.tools.infrastructure.api.database.passwordoverride.overrideAdminPassword
import com.atlassian.performance.tools.infrastructure.api.dataset.Dataset
import com.atlassian.performance.tools.infrastructure.api.dataset.HttpDatasetPackage
import com.atlassian.performance.tools.infrastructure.api.jira.JiraHomePackage
import com.atlassian.performance.tools.lib.infrastructure.AdminDataset
import com.atlassian.performance.tools.lib.infrastructure.AppNukingJiraHome
import com.atlassian.performance.tools.lib.infrastructure.ConfigurableMysqlDatabase
import com.atlassian.performance.tools.lib.overrideDatabase
import com.atlassian.performance.tools.lib.overrideJiraHome
import com.atlassian.performance.tools.lib.toExistingFile
import java.net.URI
import java.nio.file.Paths
import java.time.Duration

class HwrDatasetCatalogue {

    fun xl7Mysql() =
        URI("https://jpt-custom-datasets-storage-a008820-datasetbucket-1sjxdtrv5hdhj.s3-eu-west-1.amazonaws.com/")
            .resolve("dataset-b9618677-7852-426e-9ca6-19dc11c49ddb/")
            .let { uri ->
                Dataset(
                    label = "7M issues JSW 7 MySQL",
                    database = ConfigurableMysqlDatabase(
                        source = HttpDatasetPackage(
                            uri = uri.resolve("database.tar.bz2"),
                            downloadTimeout = Duration.ofMinutes(55)
                        ),
                        extraDockerArgs = listOf(
                            "--max_connections=151",
                            "--innodb-buffer-pool-size=40G",
                            "--innodb-log-file-size=2146435072"
                        )
                    ),
                    jiraHomeSource = JiraHomePackage(
                        HttpDatasetPackage(
                            uri = uri.resolve("jirahome.tar.bz2"),
                            downloadTimeout = Duration.ofMinutes(55)
                        )
                    )
                )
            }.let { dataset ->
                AdminDataset(
                    dataset = fix(dataset),
                    adminLogin = "admin",
                    adminPassword = "admin"
                )
            }

    fun xl8Mysql() = DatasetCatalogue().custom(
        location = StorageLocation(
            uri = URI("s3://jpt-custom-datasets-storage-a008820-datasetbucket-1nrja8d1upind/")
                .resolve("parent/")
                .resolve("v02-disable-backup-service-c4a3be0f-8e39-409c-9107-7c236d6a3b29/")
                .resolve("child/")
                .resolve("v01-publish-8.20.0-463e3936-cc2e-487c-b126-1fb93cf17de2"),
            region = Regions.EU_CENTRAL_1
        ),
        label = "7M issues JSW 8 MySQL",
        databaseDownload = Duration.ofHours(1),
        jiraHomeDownload = Duration.ofHours(1)
    ).let { dataset ->
        AdminDataset(
            dataset = fix(dataset),
            adminLogin = "admin",
            adminPassword = "admin"
        )
    }

    fun xl9Mysql() = DatasetCatalogue().custom(
        location = StorageLocation(
            uri = URI("s3://jpt-custom-datasets-storage-a008820-datasetbucket-1nrja8d1upind/")
                .resolve("parent/")
                .resolve("v01-publish-8.20.0-463e3936-cc2e-487c-b126-1fb93cf17de2/")
                .resolve("child/")
                .resolve("v01-publish-9.0.0-RC01-408add83-7576-4424-a8a6-0e6a44381b12"),
            region = Regions.EU_CENTRAL_1
        ),
        label = "7M issues JSW 9 MySQL",
        databaseDownload = Duration.ofHours(1),
        jiraHomeDownload = Duration.ofHours(1)
    ).let { dataset ->
        AdminDataset(
            dataset = fix(dataset),
            adminLogin = "admin",
            adminPassword = "admin"
        )
    }

    fun l7Mysql() = DatasetCatalogue().custom(
        location = StorageLocation(
            uri = URI("s3://jpt-custom-datasets-storage-a008820-datasetbucket-1sjxdtrv5hdhj/")
                .resolve("a12fc4c5-3973-41f0-bf56-ede393677028"),
            region = Regions.EU_WEST_1
        ),
        label = "1M issues JSW 7 MySQL",
        databaseDownload = Duration.ofMinutes(20),
        jiraHomeDownload = Duration.ofMinutes(20)
    ).let { dataset ->
        AdminDataset(
            dataset = fix(dataset),
            adminLogin = "admin",
            adminPassword = "admin"
        )
    }

    fun l9Mysql() = DatasetCatalogue().custom(
        location = StorageLocation(
            uri = URI("s3://jpt-custom-datasets-storage-a008820-datasetbucket-1nrja8d1upind/")
                .resolve("parent/")
                .resolve("v01-publish-8.20.0-88cdcb19-4183-450e-9b88-447cbf85be35/")
                .resolve("child/")
                .resolve("v01-publish-9.0.0-RC01-ea89f30b-6060-402e-a728-b13ed05d015a"),
            region = Regions.EU_CENTRAL_1
        ),
        label = "1M issues JSW 9 MySQL",
        databaseDownload = Duration.ofMinutes(30),
        jiraHomeDownload = Duration.ofMinutes(30)
    ).let { dataset ->
        AdminDataset(
            dataset = fix(dataset),
            adminLogin = "admin",
            adminPassword = "admin"
        )
    }

    fun l8Mysql() = DatasetCatalogue().custom(
        location = StorageLocation(
            uri = URI("s3://jpt-custom-datasets-storage-a008820-datasetbucket-1nrja8d1upind/")
                .resolve("parent/")
                .resolve("v01-disable-backup-service-d6c3ed65-2382-460c-9359-90424afe9ea2/")
                .resolve("child/")
                .resolve("v01-publish-8.20.0-88cdcb19-4183-450e-9b88-447cbf85be35"),
            region = Regions.EU_CENTRAL_1
        ),
        label = "1M issues JSW 8 MySQL",
        databaseDownload = Duration.ofMinutes(30),
        jiraHomeDownload = Duration.ofMinutes(30)
    ).let { dataset ->
        AdminDataset(
            dataset = fix(dataset),
            adminLogin = "admin",
            adminPassword = "admin"
        )
    }

    private fun fix(
        dataset: Dataset
    ): Dataset = dataset
        .overrideDatabase {
            overrideLicense(it)
                .overrideAdminPassword(
                    "admin",
                    "{PKCS5S2}Tdl0jlvcysmT9PiRWkZZ1Hr4uHO2OJHvkd3siXt/8OnL7GczWsyzB1tMwkJlI2eX"
                )
                .build()
        }.overrideJiraHome {
            AppNukingJiraHome(it.jiraHomeSource)
        }

    private fun overrideLicense(
        dataset: Dataset
    ): Database {
        val localLicense = Paths.get("jira-license.txt")
        return LicenseOverridingMysql
            .Builder(dataset.database)
            .licenseFiles(
                listOf(
                    localLicense
                        .toExistingFile()
                        ?: throw Exception("Put a Jira license to ${localLicense.toAbsolutePath()}")
                )
            )
            .build()
    }
}
