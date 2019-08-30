package com.atlassian.performance.tools.hardware.tuning

import com.amazonaws.services.ec2.model.InstanceType.*
import com.atlassian.performance.tools.hardware.ApplicationScale
import com.atlassian.performance.tools.hardware.Hardware
import com.atlassian.performance.tools.infrastructure.api.jira.JiraJvmArgs
import com.atlassian.performance.tools.infrastructure.api.jira.JiraLaunchTimeouts
import com.atlassian.performance.tools.infrastructure.api.jira.JiraNodeConfig
import com.atlassian.performance.tools.infrastructure.api.jvm.JvmArg
import java.time.Duration

class HeapTuning(
    private val desiredGigabytes: Int
) : JiraNodeTuning {

    override fun tune(
        nodeConfig: JiraNodeConfig,
        hardware: Hardware,
        scale: ApplicationScale
    ): JiraNodeConfig {
        return JiraNodeConfig.Builder(nodeConfig)
            .jvmArgs(
                bumpHeap(hardware)
            )
            .launchTimeouts(
                JiraLaunchTimeouts.Builder()
                    .initTimeout(Duration.ofMinutes(10))
                    .offlineTimeout(Duration.ofMinutes(15))
                    .build()
            )
            .build()
    }

    private fun bumpHeap(
        hardware: Hardware
    ): JiraJvmArgs {
        val maxGigabytes = when (hardware.jira) {
            C52xlarge -> 12
            C54xlarge -> 25
            C48xlarge -> 50
            C59xlarge -> 60
            C518xlarge -> 120
            else -> throw Exception("Don't know the max heap for ${hardware.jira}")
        }
        val actualGigabytes = maxGigabytes
            .coerceAtMost(desiredGigabytes)
            .let { avoidCompressedOopsPenalty(it) }
        val extraArgs = if (actualGigabytes > 30) {
            listOf(JvmArg("-XX:+UseG1GC"))
        } else {
            emptyList()
        }
        val actualGigabytesArg = "${actualGigabytes}G"
        return JiraJvmArgs(
            xms = actualGigabytesArg,
            xmx = actualGigabytesArg,
            extra = extraArgs
        )
    }

    private fun avoidCompressedOopsPenalty(
        desiredGigabytes: Int
    ): Int {
        return if (desiredGigabytes in (32..40)) {
            31
        } else {
            desiredGigabytes
        }
    }
}
