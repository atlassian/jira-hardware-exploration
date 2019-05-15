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
        download(connection)
        connection.execute("tar -xzf $jdkArchive")
        connection.execute("echo '${use()}' >> ~/.bashrc")
    }

    override fun use(): String = "export PATH=$jdkBin:$bin:${'$'}PATH"

    override fun command(options: String) = "${jdkBin}java $options"

    private fun download(connection: SshConnection) {
        IdempotentAction("download JDK") {
            connection.execute(
                cmd = "wget $jdkUrl --header 'Cookie: oraclelicense=accept-securebackup-cookie' --server-response -q",
                timeout = Duration.ofSeconds(65)
            )
        }.retry(3, StaticBackoff(Duration.ofSeconds(5)))
    }
}
