package com.atlassian.performance.tools.lib.awsinfrastructure

import com.atlassian.performance.tools.aws.api.Storage
import com.atlassian.performance.tools.infrastructure.api.virtualusers.ResultsTransport
import com.atlassian.performance.tools.ssh.api.SshConnection
import java.time.Duration

internal class CopiedS3ResultsTransport(
    private val results: Storage
) : ResultsTransport {

    override fun transportResults(
        targetDirectory: String,
        sshConnection: SshConnection
    ) {
        CopiedAwsCli().upload(results.location, sshConnection, targetDirectory, Duration.ofMinutes(10))
    }

    override fun toString(): String {
        return "S3ResultsTransport(results=$results)"
    }
}
