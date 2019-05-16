package com.atlassian.performance.tools.hardware

import com.amazonaws.services.ec2.model.InstanceType
import com.atlassian.performance.tools.virtualusers.api.TemporalRate
import org.hamcrest.CoreMatchers.equalTo
import org.junit.Assert.assertThat
import org.junit.Test
import java.time.Duration

class HardwareTest {

    @Test
    fun shouldEstimateCost() {
        val hardware = Hardware(
            jira = InstanceType.C54xlarge,
            nodeCount = 5,
            db = InstanceType.M44xlarge
        )

        val cost = hardware.estimateCost()

        assertThat(cost, equalTo(TemporalRate(3_957.12, Duration.ofDays(30))))
    }
}