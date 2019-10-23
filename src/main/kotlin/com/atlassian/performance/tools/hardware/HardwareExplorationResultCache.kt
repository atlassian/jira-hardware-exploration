package com.atlassian.performance.tools.hardware

import com.amazonaws.services.ec2.model.InstanceType
import com.atlassian.performance.tools.io.api.ensureParentDirectory
import com.atlassian.performance.tools.lib.ActionError
import com.atlassian.performance.tools.lib.OverallError
import com.atlassian.performance.tools.lib.Ratio
import com.atlassian.performance.tools.virtualusers.api.TemporalRate
import java.nio.file.Path
import java.time.Duration
import javax.json.Json.*
import javax.json.JsonArray
import javax.json.JsonNumber
import javax.json.JsonObject
import javax.json.JsonValue

class HardwareExplorationResultCache(
    private val cache: Path
) {

    fun write(
        results: List<HardwareExplorationResult>
    ) {
        val json = writeResults(results)
        cache
            .toFile()
            .ensureParentDirectory()
            .bufferedWriter()
            .use { it.write(json.toString()) }
    }

    private fun writeResults(
        results: List<HardwareExplorationResult>
    ): JsonArray {
        val builder = createArrayBuilder()
        results.map { writeResult(it) }.forEach { builder.add(it) }
        return builder.build()
    }

    private fun writeResult(
        result: HardwareExplorationResult
    ): JsonObject {
        val builder = createObjectBuilder()
        builder.add("decision", writeDecision(result.decision))
        if (result.testResult != null) {
            builder.add("testResult", writeTestResult(result.testResult))
        }
        return builder.build()
    }

    private fun writeDecision(
        decision: HardwareExplorationDecision
    ): JsonObject = decision.run {
        createObjectBuilder()
            .add("hardware", writeHardware(hardware))
            .add("worthExploring", worthExploring)
            .add("reason", reason)
            .build()
    }

    private fun writeHardware(
        hardware: Hardware
    ): JsonObject = hardware.run {
        createObjectBuilder()
            .add("jira", jira.toString())
            .add("nodeCount", nodeCount)
            .add("db", db.toString())
            .build()
    }

    private fun writeTestResult(
        testResult: HardwareTestResult
    ): JsonObject = testResult.run {
        createObjectBuilder()
            .add("apdex", apdex)
            .add("apdexes", createArrayBuilder(apdexes).build())
            .add("overallError", writeOverallError(overallError))
            .add("overallErrors", writeOverallErrors(overallErrors))
            .also { if (maxActionError != null) it.add("maxActionError", writeMaxActionError(maxActionError)) }
            .also { if (maxActionErrors != null) it.add("maxActionErrors", writeMaxActionErrors(maxActionErrors)) }
            .add("httpThroughput", writeThroughput(httpThroughput))
            .add("httpThroughputs",
                createArrayBuilder()
                    .also { array ->
                        httpThroughputs
                            .map { writeThroughput(it) }
                            .forEach { array.add(it) }
                    }
                    .build()
            )
            .add("hardware", writeHardware(hardware))
            .build()
    }

    private fun writeOverallError(
        overallError: OverallError
    ): JsonValue = overallError.run {
        createObjectBuilder()
            .add("ratio", ratio.proportion)
            .build()
    }

    private fun writeOverallErrors(
        overallErrors: List<OverallError>
    ): JsonValue {
        val builder = createArrayBuilder()
        overallErrors.map { writeOverallError(it) }.forEach { builder.add(it) }
        return builder.build()
    }

    private fun writeMaxActionError(
        maxActionError: ActionError
    ): JsonValue = maxActionError.run {
        createObjectBuilder()
            .add("ratio", ratio.proportion)
            .add("actionLabel", actionLabel)
            .build()
    }

    private fun writeMaxActionErrors(
        maxActionErrors: List<ActionError>
    ): JsonValue {
        val builder = createArrayBuilder()
        maxActionErrors.map { writeMaxActionError(it) }.forEach { builder.add(it) }
        return builder.build()
    }

    private fun writeThroughput(
        throughput: TemporalRate
    ): JsonObject = throughput.run {
        createObjectBuilder()
            .add("change", change)
            .add("time", writeDuration(time))
            .build()
    }

    private fun writeDuration(
        duration: Duration
    ): String = duration.toString()

    fun read(): List<HardwareExplorationResult> = cache
        .toFile()
        .bufferedReader()
        .use { createReader(it).read() }
        .asJsonArray()
        .let { readResults(it) }

    private fun readResults(
        json: JsonArray
    ): List<HardwareExplorationResult> = json
        .map { readResult(it.asJsonObject()) }

    private fun readResult(
        json: JsonObject
    ): HardwareExplorationResult = json.run {
        HardwareExplorationResult(
            decision = readDecision(getJsonObject("decision")),
            testResult = getJsonObject("testResult")?.let { readTestResult(it) }
        )
    }

    private fun readDecision(
        json: JsonObject
    ): HardwareExplorationDecision = json.run {
        HardwareExplorationDecision(
            hardware = readHardware(getJsonObject("hardware")),
            worthExploring = getBoolean("worthExploring"),
            reason = getString("reason")
        )
    }

    private fun readHardware(
        json: JsonObject
    ): Hardware = json.run {
        Hardware(
            jira = (getString("jira", null) ?: getString("instanceType")).let { InstanceType.fromValue(it) },
            nodeCount = getInt("nodeCount"),
            db = getString("db", null)?.let { InstanceType.fromValue(it) } ?: InstanceType.M4Xlarge
        )
    }

    private fun readTestResult(
        json: JsonObject
    ): HardwareTestResult = json.run {
        HardwareTestResult(
            hardware = readHardware(getJsonObject("hardware")),
            apdex = getJsonNumber("apdex").doubleValue(),
            apdexes = getJsonArray("apdexes").map { it as JsonNumber }.map { it.doubleValue() },
            overallError = readOverallError(this),
            overallErrors = readOverallErrors(this),
            maxActionError = getJsonObject("maxActionError")?.let { readActionError(it) },
            maxActionErrors = getJsonArray("maxActionErrors")?.map { it.asJsonObject() }?.map { readActionError(it) },
            httpThroughput = readThroughput(getJsonObject("httpThroughput")),
            httpThroughputs = getJsonArray("httpThroughputs").map { it.asJsonObject() }.map { readThroughput(it) },
            results = emptyList()
        )
    }

    private fun readOverallError(
        json: JsonObject
    ): OverallError = json
        .getJsonObject("overallError")
        ?.getJsonNumber("ratio")
        ?.doubleValue()
        ?.let { OverallError(Ratio(it)) }
        ?: json
            .getJsonNumber("errorRate")
            .doubleValue()
            .let { OverallError(Ratio(it)) }

    private fun readOverallErrors(
        json: JsonObject
    ): List<OverallError> = json
        .getJsonArray("overallErrors")
        ?.map { it.asJsonObject() }
        ?.map { readOverallError(it) }
        ?: json
            .getJsonArray("errorRates")
            .map { it as JsonNumber }
            .map { it.doubleValue() }
            .map { OverallError(Ratio(it)) }

    private fun readActionError(
        json: JsonObject
    ): ActionError = json.run {
        ActionError(
            actionLabel = getString("actionLabel"),
            ratio = getJsonNumber("ratio")
                .doubleValue()
                .let { Ratio(it) }
        )
    }


    private fun readThroughput(
        json: JsonObject
    ): TemporalRate = json.run {
        TemporalRate(
            change = getJsonNumber("change").doubleValue(),
            time = readDuration(getString("time"))
        )
    }

    private fun readDuration(
        string: String
    ): Duration = Duration.parse(string)
}
