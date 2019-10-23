package com.atlassian.performance.tools.lib.infrastructure

import com.atlassian.performance.tools.ssh.api.SshConnection
import org.apache.logging.log4j.Level
import java.time.Duration
import java.util.*

internal class CopiedDockerImage(
    private val name: String,
    private val pullTimeout: Duration = Duration.ofMinutes(1)
) {

    private val docker = CopiedDocker()

    fun run(
        ssh: SshConnection,
        parameters: String = "",
        arguments: String = ""
    ): String {
        docker.install(ssh)
        val containerName = "jpt-" + UUID.randomUUID()
        ssh.execute(
            cmd = "sudo docker pull $name",
            timeout = pullTimeout,
            stdout = Level.TRACE,
            stderr = Level.WARN
        )
        ssh.execute("sudo docker run -d $parameters --name $containerName $name $arguments")
        return containerName
    }
}