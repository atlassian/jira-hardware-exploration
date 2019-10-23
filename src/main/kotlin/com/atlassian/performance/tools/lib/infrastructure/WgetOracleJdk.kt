package com.atlassian.performance.tools.lib.infrastructure

import com.atlassian.performance.tools.infrastructure.api.jvm.Jstat
import com.atlassian.performance.tools.infrastructure.api.jvm.OracleJDK
import com.atlassian.performance.tools.infrastructure.api.jvm.VersionedJavaDevelopmentKit
import com.atlassian.performance.tools.jvmtasks.api.IdempotentAction
import com.atlassian.performance.tools.lib.jvmtasks.StaticBackoff
import com.atlassian.performance.tools.ssh.api.SshConnection
import java.net.URI
import java.time.Duration

/**
 * Gives more info than [OracleJDK] to debug JPERF-474.
 */
class WgetOracleJdk : VersionedJavaDevelopmentKit {
    private val jdkUpdate = 131
    private val jdkArchive = "jdk-8u$jdkUpdate-linux-x64.tar.gz"
    private val jdkUrl = URI("https://download.oracle.com/otn-pub/java/jdk/")
        .resolve("8u$jdkUpdate-b11/d54c1d3a095b4ff2b6607d096fa80163/$jdkArchive")
    private val jdkBin = "~/jdk1.8.0_$jdkUpdate/jre/bin/"
    private val bin = "~/jdk1.8.0_$jdkUpdate/bin/"
    override val jstatMonitoring = Jstat(bin)

    @Deprecated(
        message = "Use JavaDevelopmentKit.jstatMonitoring instead.",
        replaceWith = ReplaceWith("jstatMonitoring")
    )
    val jstat = jstatMonitoring

    override fun getMajorVersion() = 8

    override fun install(connection: SshConnection) {
        val archive = download(connection)
        connection.execute("tar -xzf $archive")
        connection.execute("echo '${use()}' >> ~/.bashrc")
    }

    override fun use(): String = "export PATH=$jdkBin:$bin:${'$'}PATH"

    override fun command(options: String) = "${jdkBin}java $options"

    private fun download(connection: SshConnection): String {
        var attempt = 0
        return IdempotentAction("download JDK") {
            attempt++
            val cookie = "--header 'Cookie: oraclelicense=accept-securebackup-cookie'"
            val archive = "$jdkArchive.$attempt"
            connection.execute(
                cmd = "wget $jdkUrl $cookie --server-response -q -O $archive",
                timeout = Duration.ofSeconds(65)
            )
            checkChecksum(archive, connection)
            return@IdempotentAction archive
        }.retry(3, StaticBackoff(Duration.ofSeconds(5)))
    }

    private fun checkChecksum(
        file: String,
        shell: SshConnection
    ) {
        val actual = shell.execute("cksum $file").output.trim()
        val expected = "19878902 185540433 $file"
        if (actual != expected) {
            throw Exception("Checksum failed. Expected: '$expected', got: '$actual'")
        }
    }
}
