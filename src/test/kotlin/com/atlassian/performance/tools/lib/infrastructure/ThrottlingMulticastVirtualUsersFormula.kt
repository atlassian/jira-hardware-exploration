package com.atlassian.performance.tools.lib.infrastructure

import com.atlassian.performance.tools.aws.api.Aws
import com.atlassian.performance.tools.aws.api.Investment
import com.atlassian.performance.tools.aws.api.SshKey
import com.atlassian.performance.tools.aws.api.Storage
import com.atlassian.performance.tools.awsinfrastructure.api.virtualusers.MulticastVirtualUsersFormula
import com.atlassian.performance.tools.awsinfrastructure.api.virtualusers.ProvisionedVirtualUsers
import com.atlassian.performance.tools.awsinfrastructure.api.virtualusers.VirtualUsersFormula
import com.atlassian.performance.tools.infrastructure.api.virtualusers.ResultsTransport
import java.util.concurrent.Future

class ThrottlingMulticastVirtualUsersFormula(
    private val formula: MulticastVirtualUsersFormula
) : VirtualUsersFormula<ThrottlingMulticastVirtualUsers> {

    override fun provision(
        investment: Investment,
        shadowJarTransport: Storage,
        resultsTransport: ResultsTransport,
        key: Future<SshKey>,
        roleProfile: String,
        aws: Aws
    ): ProvisionedVirtualUsers<ThrottlingMulticastVirtualUsers> {
        val provisioned = formula.provision(investment, shadowJarTransport, resultsTransport, key, roleProfile, aws)
        return ProvisionedVirtualUsers(
            ThrottlingMulticastVirtualUsers(provisioned.virtualUsers),
            provisioned.resource
        )
    }
}
