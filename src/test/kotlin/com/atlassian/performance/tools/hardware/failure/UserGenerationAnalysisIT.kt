package com.atlassian.performance.tools.hardware.failure

import com.amazonaws.services.s3.transfer.TransferManagerBuilder
import com.atlassian.performance.tools.hardware.IntegrationTestRuntime
import com.atlassian.performance.tools.hardware.IntegrationTestRuntime.rootWorkspace
import com.atlassian.performance.tools.lib.LogConfigurationFactory
import com.atlassian.performance.tools.lib.s3cache.S3Cache
import org.apache.logging.log4j.core.config.ConfigurationFactory
import org.junit.Before
import org.junit.Test
import java.io.File

class UserGenerationAnalysisIT {

    private val workspace = rootWorkspace.isolateTask("UserGenerationAnalysisIT")

    @Before
    fun setUp() {
        ConfigurationFactory.setConfigurationFactory(LogConfigurationFactory(workspace))
    }

    @Test
    fun shouldCorrelate503s() {
        val parsedRuns = getResults()
            .walkTopDown()
            .filter { it.name == "runs" }
            .flatMap { it.listFiles().asSequence() }
            .filter { it.resolve("status.txt").exists() }
            .map { parse(it) }
            .toList()
        val commonJiraErrors = parsedRuns
            .map { it.jiraRestFailures.keys }
            .reduce { acc, list -> acc.intersect(list) }
        val runsWithInterestingErrors = parsedRuns.map { run ->
            ParsedRun(
                run.directory,
                run.userGenerationFailures,
                run.jiraRestFailures.filterKeys { it !in commonJiraErrors }
            )
        }
        println("runsWithInterestingErrors = $runsWithInterestingErrors")
        val okRuns = runsWithInterestingErrors.filter { it.userGenerationFailures == 0 }
        val badRuns = runsWithInterestingErrors - okRuns
        val okErrors = okRuns.flatMap { it.jiraRestFailures.keys }.distinct()
        val badErrors = badRuns.flatMap { it.jiraRestFailures.keys }.distinct()
        println("okErrors = $okErrors")
        println("badErrors = $badErrors")
    }

    private fun parse(
        directory: File
    ): ParsedRun = ParsedRun(
        directory = directory,
        userGenerationFailures = directory
            .walkTopDown()
            .filter { it.name == "virtual-users-error.log" }
            .filter { it.readText().contains("Caused by: java.lang.Exception: Failed to create a new user") }
            .count(),
        jiraRestFailures = directory
            .walkTopDown()
            .filter { it.name == "atlassian-jira.log" }
            .toList()
            .flatMap { log ->
                log
                    .readLines()
                    .filter { it.contains("/rest/api/2/user") }
                    .map { it.substringAfter("/rest/api/2/user") }
            }
            .groupingBy { it }
            .eachCount()
    )

    private fun getResults(): File {
        val cacheKey = "HardwareRecommendationEngineIT/54f5311380d3cf38270713de4a35d96e3fc8a58f"
        val local = rootWorkspace.directory.resolve(cacheKey).toFile()
        if (local.exists()) {
            return local
        }
        S3Cache(
            transfer = TransferManagerBuilder.standard()
                .withS3Client(IntegrationTestRuntime.prepareAws().s3)
                .build(),
            bucketName = "quicksilver-jhwr-cache-ireland",
            cacheKey = cacheKey,
            localPath = local.toPath(),
            etags = rootWorkspace.directory.resolve(".etags")
        ).download()
        return local
    }

    private data class ParsedRun(
        val directory: File,
        val userGenerationFailures: Int,
        val jiraRestFailures: Map<String, Int>
    )
}
