package com.atlassian.performance.tools.lib.awsinfrastructure

import com.atlassian.performance.tools.infrastructure.api.distribution.ProductDistribution
import com.atlassian.performance.tools.ssh.api.SshConnection

internal class ProductDistributionChain(
    private vararg val distributions: ProductDistribution
) : ProductDistribution {
    private val exception = Exception("Cannot find Jira artifact")

    override fun install(
        ssh: SshConnection,
        destination: String
    ): String = distributions
        .asSequence()
        .mapNotNull { tryInstall(it, ssh, destination) }
        .firstOrNull()
        ?: throw exception

    private fun tryInstall(
        distribution: ProductDistribution,
        ssh: SshConnection,
        destination: String
    ): String? = try {
        distribution.install(ssh, destination)
    } catch (e: Exception) {
        exception.addSuppressed(e)
        null
    }
}
