package com.atlassian.performance.tools.lib.infrastructure

import com.atlassian.performance.tools.infrastructure.HttpResource
import com.atlassian.performance.tools.infrastructure.api.os.Ubuntu
import com.atlassian.performance.tools.ssh.api.SshConnection
import java.net.URI
import java.time.Duration

/**
 * [Copied](https://github.com/atlassian/infrastructure/blob/release-4.12.0/src/main/kotlin/com/atlassian/performance/tools/infrastructure/ChromiumInstaller.kt)
 * and overridden the download timeout.
 */
internal class PatientChromiumInstaller(private val uri: URI) {
    internal fun install(ssh: SshConnection) {
        val ubuntu = Ubuntu()
        ubuntu.install(
            ssh,
            listOf(
                "unzip",
                "libx11-xcb1",
                "libxcomposite1",
                "libxdamage1",
                "libxi6",
                "libxtst6",
                "libnss3",
                "libcups2",
                "libxss1",
                "libxrandr2",
                "libasound2",
                "libpango1.0",
                "libatk1.0-0",
                "libatk-bridge2.0",
                "libgtk-3-0"
            ),
            Duration.ofMinutes(5)
        )
        HttpResource(uri).download(ssh, "chromium.zip", Duration.ofMinutes(4))
        ssh.execute("unzip chromium.zip")
        ssh.execute("sudo ln -s `pwd`/chrome-linux/chrome /usr/bin/chrome")
    }
}
