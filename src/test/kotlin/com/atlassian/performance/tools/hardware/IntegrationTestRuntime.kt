package com.atlassian.performance.tools.hardware

import com.amazonaws.auth.*
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.regions.Regions
import com.atlassian.performance.tools.aws.api.Aws
import com.atlassian.performance.tools.aws.api.TextCapacityMediator
import com.atlassian.performance.tools.workspace.api.RootWorkspace
import java.nio.file.Paths
import java.time.Duration
import java.util.*

object IntegrationTestRuntime {

    val rootWorkspace = RootWorkspace(Paths.get("build"))
    private const val roleArn: String = "arn:aws:iam::695067801333:role/JPT-dev"
    private val region = Regions.EU_WEST_1

    fun prepareAws() = Aws.Builder(region)
        .credentialsProvider(
            AWSCredentialsProviderChain(
                WebIdentityTokenCredentialsProvider.builder()
                    .roleArn(roleArn)
                    .roleSessionName(UUID.randomUUID().toString())
                    .build(),
                DefaultAWSCredentialsProviderChain()
            )
        )
        .regionsWithHousekeeping(listOf(Regions.EU_WEST_1))
        .capacity(TextCapacityMediator(region))
        .batchingCloudformationRefreshPeriod(Duration.ofSeconds(20))
        .build()
}
