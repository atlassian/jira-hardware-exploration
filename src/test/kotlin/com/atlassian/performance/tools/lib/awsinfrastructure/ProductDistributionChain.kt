package com.atlassian.performance.tools.lib.awsinfrastructure

import com.atlassian.performance.tools.infrastructure.api.distribution.ProductDistribution
import com.atlassian.performance.tools.ssh.api.SshConnection

internal class ProductDistributionChain(
    private vararg val distributions: ProductDistribution
) : ProductDistribution {

    override fun install(
        ssh: SshConnection,
        destination: String
    ): String {
        val exceptionChain = Exception("Cannot find Jira artifact")
        return distributions
            .asSequence()
            .mapNotNull { tryInstall(it, ssh, destination, exceptionChain) }
            .firstOrNull()
            ?: throw exceptionChain
    }

    private fun tryInstall(
        distribution: ProductDistribution,
        ssh: SshConnection,
        destination: String,
        exceptionChain: Exception
    ): String? = try {
        distribution.install(ssh, destination)
    } catch (e: Exception) {
        exceptionChain.addSuppressed(e)
        null
    }
}
