package com.atlassian.performance.tools.hardware

import com.atlassian.performance.tools.aws.api.Aws
import com.atlassian.performance.tools.aws.api.Investment
import com.atlassian.performance.tools.hardware.failure.FailureTolerance
import com.atlassian.performance.tools.hardware.guidance.ExplorationGuidance
import com.atlassian.performance.tools.hardware.tuning.JiraNodeTuning
import com.atlassian.performance.tools.infrastructure.api.distribution.ProductDistribution
import com.atlassian.performance.tools.io.api.directories
import com.atlassian.performance.tools.lib.OverallError
import com.atlassian.performance.tools.lib.Ratio
import com.atlassian.performance.tools.lib.readResult
import com.atlassian.performance.tools.lib.s3cache.S3Cache
import com.atlassian.performance.tools.report.api.FullReport
import com.atlassian.performance.tools.report.api.result.EdibleResult
import com.atlassian.performance.tools.virtualusers.api.TemporalRate
import com.atlassian.performance.tools.workspace.api.TaskWorkspace
import com.atlassian.performance.tools.workspace.api.TestWorkspace
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.io.File
import java.time.Duration

class HardwareExistingResults(
    private val task: TaskWorkspace,
    private val pastFailures: FailureTolerance,
    private val metric: HardwareMetric
) {
    private val logger: Logger = LogManager.getLogger(this::class.java)

    fun reuseResults(
        hardware: Hardware
    ): List<HardwareTestResult> {
        val reusableResults = listPreviousRuns(hardware).mapNotNull { reuseResult(hardware, it) }
        if (reusableResults.isNotEmpty()) {
            logger.debug("Reusing ${reusableResults.size} results")
        }
        return reusableResults
    }

    private fun reuseResult(
        hardware: Hardware,
        previousRun: File
    ): HardwareTestResult? {
        val workspace = TestWorkspace(previousRun.toPath())
        val cohortResult = workspace.readResult(hardware.nameCohort(workspace))
        val failure = cohortResult.failure
        return if (failure == null) {
            metric.score(hardware, cohortResult)
        } else {
            pastFailures.handle(failure, workspace)
            null
        }
    }

    fun listPreviousRuns(
        hardware: Hardware
    ): List<File> {
        val hardwareDirectory = hardware
            .isolateRuns(task)
            .directory
            .toFile()
        return if (hardwareDirectory.isDirectory) {
            hardwareDirectory.directories()
        } else {
            emptyList()
        }
    }

    fun coalesce(
        results: List<HardwareTestResult>,
        hardware: Hardware
    ): HardwareTestResult {
        val apdexes = results.map { it.apdex }
        val throughputUnit = Duration.ofSeconds(1)
        val throughputs = results
            .map { it.httpThroughput }
            .map { it.scaleTime(throughputUnit) }
            .map { it.change }
        val errorRates = results.map { it.overallError }
        val testResult = HardwareTestResult(
            hardware = hardware,
            apdex = apdexes.average(),
            apdexes = results.flatMap { it.apdexes },
            httpThroughput = TemporalRate(throughputs.average(), throughputUnit),
            httpThroughputs = results.flatMap { it.httpThroughputs },
            results = results.flatMap { it.results },
            overallError = OverallError(Ratio(errorRates.map { it.ratio.proportion }.average())),
            overallErrors = results.flatMap { it.overallErrors },
            maxActionError = results.mapNotNull { it.maxActionError }.maxBy { it.ratio }!!,
            maxActionErrors = results.flatMap { it.maxActionErrors ?: emptyList() }
        )
        val postProcessedResults = results.flatMap { it.results }.map { metric.postProcess(it) }
        reportRaw(postProcessedResults, hardware)
        return testResult
    }

    private fun reportRaw(
        results: List<EdibleResult>,
        hardware: Hardware
    ) {
        val workspace = hardware.isolateSubTask(task, "sub-test-comparison")
        FullReport().dump(
            results = results,
            workspace = TestWorkspace(workspace.directory)
        )
    }
}
