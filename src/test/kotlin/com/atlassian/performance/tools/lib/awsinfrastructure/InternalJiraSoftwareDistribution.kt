package com.atlassian.performance.tools.lib.awsinfrastructure

import com.atlassian.performance.tools.awsinfrastructure.api.storage.ApplicationStorage
import com.atlassian.performance.tools.awsinfrastructure.api.storage.S3Artifact
import com.atlassian.performance.tools.infrastructure.api.distribution.ProductDistribution
import com.atlassian.performance.tools.ssh.api.SshConnection

internal class InternalJiraSoftwareDistribution(
    private val version: String
) : ProductDistribution {
    override fun install(
        ssh: SshConnection,
        destination: String
    ): String = InternalJswStorage().download(ssh, destination)

    @Suppress("DEPRECATION")
    private inner class InternalJswStorage : ApplicationStorage {
        override val possibleLocations: List<S3Artifact> = listOf(
            S3Artifact(
                region = "us-east-1",
                bucketName = "downloads-internal-us-east-1",
                archivesLocation = "private/jira/$version",
                archiveName = "atlassian-jira-software-$version-standalone.tar.gz"
            )
        )
    }
}
