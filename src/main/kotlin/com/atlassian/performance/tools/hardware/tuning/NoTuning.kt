package com.atlassian.performance.tools.hardware.tuning

import com.atlassian.performance.tools.hardware.ApplicationScale
import com.atlassian.performance.tools.hardware.Hardware
import com.atlassian.performance.tools.infrastructure.api.jira.JiraNodeConfig

class NoTuning : JiraNodeTuning {

    override fun tune(
        nodeConfig: JiraNodeConfig,
        hardware: Hardware,
        scale: ApplicationScale
    ): JiraNodeConfig {
        return nodeConfig
    }
}
