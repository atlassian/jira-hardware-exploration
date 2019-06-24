package com.atlassian.performance.tools.lib

import com.atlassian.performance.tools.io.api.ensureDirectory
import org.assertj.core.api.Assertions
import org.hamcrest.CoreMatchers.equalTo
import org.junit.Assert.assertThat
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.Duration
import java.util.zip.ZipFile

class AccessLogThroughputTest {

    @Test
    fun shouldGauge() {
        val rawResults = getResource("/JIRA-JPTC-1339")

        val throughput = AccessLogThroughput().gauge(rawResults)

        assertThat(throughput.change, equalTo(41.70824779594451))
        assertThat(throughput.time, equalTo(Duration.ofSeconds(1)))
    }

    @Test
    fun shouldGaugeWithSquareBracketsInUri() {
        val rawResults = getResource("/QUICK-8")

        val throughput = AccessLogThroughput().gauge(rawResults)

        assertThat(throughput.change, equalTo(9.626355296080066))
        assertThat(throughput.time, equalTo(Duration.ofSeconds(1)))
    }

    @Test
    fun shouldGaugeOvernightLogs() {
        val rawResults = getResource("/QUICK-73")

        AccessLogThroughput().gauge(rawResults)
    }

    @Test
    fun shouldRejectPorousLogs() {
        val rawResults = unzip(getResource("/quick-174-duplicated-run.zip"))

        val thrown = Assertions.catchThrowable {
            AccessLogThroughput().gauge(rawResults)
        }

        Assertions.assertThat(thrown)
            .`as`("should detect porous logs and throw")
            .isNotNull()
    }

    private fun getResource(
        resourcePath: String
    ): File = File(
        this::class
            .java
            .getResource(resourcePath)
            .toURI()
    )

    private fun unzip(
        file: File
    ): File {
        val unpacked = Files.createTempDirectory("hwr-test")
        val zip = ZipFile(file)
        zip.stream().forEach { entry ->
            val unpackedEntry = unpacked.resolve(entry.name)
            if (entry.isDirectory) {
                unpackedEntry.ensureDirectory()
            } else {
                zip.getInputStream(entry).use { packedStream ->
                    unpackedEntry.toFile().outputStream().use { unpackedStream ->
                        packedStream.copyTo(unpackedStream)
                    }
                }
            }
        }
        return unpacked.toFile()
    }
}
