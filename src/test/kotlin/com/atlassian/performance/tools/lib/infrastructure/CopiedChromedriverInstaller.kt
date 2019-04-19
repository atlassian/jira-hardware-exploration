package com.atlassian.performance.tools.lib.infrastructure

import com.atlassian.performance.tools.infrastructure.HttpResource
import com.atlassian.performance.tools.infrastructure.api.os.Ubuntu
import com.atlassian.performance.tools.ssh.api.SshConnection
import java.net.URI
import java.time.Duration

/**
 * [Copied](https://github.com/atlassian/infrastructure/blob/release-4.12.0/src/main/kotlin/com/atlassian/performance/tools/infrastructure/ChromedriverInstaller.kt)
 */
internal class CopiedChromedriverInstaller(private val uri: URI) {
    internal fun install(ssh: SshConnection) {
        HttpResource(uri).download(ssh, "chromedriver.zip")
        Ubuntu().install(ssh, listOf("zip", "libglib2.0-0", "libnss3"), Duration.ofMinutes(2))
        ssh.execute("unzip -n -q chromedriver.zip")
        ssh.execute("chmod +x chromedriver")
        ssh.execute("sudo ln -s `pwd`/chromedriver /usr/bin/chromedriver")
    }
}
