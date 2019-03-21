package com.atlassian.performance.tools.hardware

import com.amazonaws.services.ec2.model.InstanceType
import com.atlassian.performance.tools.io.api.ensureParentDirectory
import com.atlassian.performance.tools.lib.Throughput
import java.nio.file.Path
import java.time.Duration
import javax.json.Json
import javax.json.JsonArray
import javax.json.JsonObject

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
        val builder = Json.createArrayBuilder()
        results.map { writeResult(it) }.forEach { builder.add(it) }
        return builder.build()
    }

    private fun writeResult(
        result: HardwareExplorationResult
    ): JsonObject {
        val builder = Json.createObjectBuilder()
        builder.add("decision", writeDecision(result.decision))
        if (result.testResult != null) {
            builder.add("testResult", writeTestResult(result.testResult))
        }
        return builder.build()
    }

    private fun writeDecision(
        decision: HardwareExplorationDecision
    ): JsonObject = decision.run {
        Json.createObjectBuilder()
            .add("hardware", writeHardware(hardware))
            .add("worthExploring", worthExploring)
            .add("reason", reason)
            .build()
    }

    private fun writeHardware(
        hardware: Hardware
    ): JsonObject = hardware.run {
        Json.createObjectBuilder()
            .add("jira", jira.toString())
            .add("nodeCount", nodeCount)
            .add("db", db.toString())
            .build()
    }

    private fun writeTestResult(
        testResult: HardwareTestResult
    ): JsonObject = testResult.run {
        Json.createObjectBuilder()
            .add("apdex", apdex)
            .add("apdexSpread", apdexSpread)
            .add("errorRate", errorRate)
            .add("errorRateSpread", errorRateSpread)
            .add("httpThroughput", writeThroughput(httpThroughput))
            .add("httpThroughputSpread", writeThroughput(httpThroughputSpread))
            .add("hardware", writeHardware(hardware))
            .build()
    }

    private fun writeThroughput(
        throughput: Throughput
    ): JsonObject = throughput.run {
        Json.createObjectBuilder()
            .add("count", count)
            .add("period", writeDuration(period))
            .build()
    }

    private fun writeDuration(
        duration: Duration
    ): String = duration.toString()

    fun read(): List<HardwareExplorationResult> = cache
        .toFile()
        .bufferedReader()
        .use { Json.createReader(it).read() }
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
            apdexSpread = getJsonNumber("apdexSpread").doubleValue(),
            errorRate = getJsonNumber("errorRate").doubleValue(),
            errorRateSpread = getJsonNumber("errorRateSpread").doubleValue(),
            httpThroughput = readThroughput(getJsonObject("httpThroughput")),
            httpThroughputSpread = readThroughput(getJsonObject("httpThroughputSpread")),
            results = emptyList()
        )
    }

    private fun readThroughput(
        json: JsonObject
    ): Throughput = json.run {
        Throughput(
            count = getJsonNumber("count").doubleValue(),
            period = readDuration(getString("period"))
        )
    }

    private fun readDuration(
        string: String
    ): Duration = Duration.parse(string)
}
