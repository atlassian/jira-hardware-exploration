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

//<<<<<<< HEAD
//private val JIRA_XL_DATASET = StorageLocation(
//    uri = URI("s3://jpt-custom-mysql-xl/dataset-7m-jira7"),
//    region = EU_WEST_1
//).let { location ->
//    Dataset(
//        label = "7M issues",
//        database = MySqlDatabase(
//            source = S3DatasetPackage(
//                artifactName = "database.tar.bz2",
//                location = location,
//                unpackedPath = "database",
//                downloadTimeout = Duration.ofMinutes(55)
//            ),
//            maxConnections = 151,
//            innodb_buffer_pool_size = "40G",
//            innodb_log_file_size = "2G"
//        ),
//        jiraHomeSource = JiraHomePackage(
//            S3DatasetPackage(
//                artifactName = "jirahome.tar.bz2",
//                location = location,
//                unpackedPath = "jirahome",
//                downloadTimeout = Duration.ofMinutes(55)
//            )
//        )
//    )
//}.overrideDatabase { original ->
//    overrideLicense(original)
//}.let { dataset ->
//    AdminDataset(
//        dataset = dataset,
//        adminLogin = "admin",
//        adminPassword = "admin"
//    )
//}
//
//private val JIRA_POST8_XL_DATASET = StorageLocation(
//    //uri = URI("s3://jpt-custom-datasets-storage-a008820-datasetbucket-dah44h6l1l8p/dataset-6ed65a53-86cb-457e-a87f-cbcce67787c3"),
//    uri = URI("s3://jpt-custom-mysql-xl/dataset-7m-jira8-reindexed"),
//    region = EU_WEST_1
//).let { location ->
//    Dataset(
//        label = "7M issues",
//        database = MySqlDatabase(
//            source = S3DatasetPackage(
//                artifactName = "database.tar.bz2",
//                location = location,
//                unpackedPath = "database",
//                downloadTimeout = Duration.ofMinutes(55)
//            ),
//            maxConnections = 151,
//            innodb_buffer_pool_size = "40G",
//            innodb_log_file_size = "2G --innodb-buffer-pool-instances=20 --innodb-buffer-pool-load-at-startup=ON --innodb-sort-buffer-size=4M --join-buffer-size=4M"
//        ),
//        jiraHomeSource = JiraHomePackage(
//            S3DatasetPackage(
//                artifactName = "jirahome.tar.bz2",
//                location = location,
//                unpackedPath = "jirahome",
//                downloadTimeout = Duration.ofMinutes(55)
//            )
//        )
//    )
//}.overrideDatabase { original ->
//    overrideLicense(original)
//}.let { dataset ->
//    AdminDataset(
//        dataset = dataset,
//        adminLogin = "admin",
//        adminPassword = "admin"
//    )
//}
//
//private val JIRA_PRE8_L_DATASET = DatasetCatalogue().custom(
//    location = StorageLocation(
//        uri = URI("s3://jpt-custom-datasets-storage-a008820-datasetbucket-1sjxdtrv5hdhj/")
//            .resolve("a12fc4c5-3973-41f0-bf56-ede393677028"),
//        region = EU_WEST_1
//    ),
//    label = "1M issues",
//    databaseDownload = Duration.ofMinutes(20),
//    jiraHomeDownload = Duration.ofMinutes(20)
//).overrideDatabase { original ->
//    overrideLicense(original)
//}.let { dataset ->
//    AdminDataset(
//        dataset = dataset,
//        adminLogin = "admin",
//        adminPassword = "admin"
//    )
//}
//
//private val JIRA_POST8_L_DATASET = DatasetCatalogue().custom(
//    location = StorageLocation(
//        uri = URI("s3://jpt-custom-datasets-storage-a008820-datasetbucket-dah44h6l1l8p/")
//            .resolve("dataset-2719279d-0b30-4050-8d98-0a9499ec36a0"),
//        region = EU_CENTRAL_1
//    ),
//    label = "1M issues",
//    databaseDownload = Duration.ofMinutes(20),
//    jiraHomeDownload = Duration.ofMinutes(20)
//).overrideDatabase { original ->
//    overrideLicense(original)
//}.let { dataset ->
//    AdminDataset(
//        dataset = dataset,
//        adminLogin = "admin",
//        adminPassword = "admin"
//    )
//}
//
//private fun overrideLicense(
//    dataset: Dataset
//): LicenseOverridingDatabase {
//    val localLicense = Paths.get("jira-license.txt")
//    return LicenseOverridingDatabase(
//        dataset.database,
//        listOf(
//            localLicense
//                .toExistingFile()
//                ?.readText()
//                ?: throw Exception("Put a Jira license to ${localLicense.toAbsolutePath()}")
//        ))
//}
//
//fun extraLarge(
//    jira8: Boolean
//) = ApplicationScale(
//    description = "Jira XL profile",
//    dataset = if (jira8) JIRA_POST8_XL_DATASET else JIRA_XL_DATASET,
//=======
private val DATASETS = HwrDatasetCatalogue()

fun extraLarge(
    jira8: Boolean,
    postgres: Boolean
): ApplicationScale = ApplicationScale(
    description = "Jira XL profile",
    dataset = when {
        jira8 && postgres -> DATASETS.xl8Postgres()
        jira8 && !postgres -> DATASETS.xl8Mysql()
        else -> throw Exception("We don't have an XL dataset matching jira8=$jira8 and postgres=$postgres")
    },
    load = VirtualUserLoad.Builder()
        .virtualUsers(150)
        .ramp(Duration.ofSeconds(90))
        .flat(Duration.ofMinutes(20))
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
