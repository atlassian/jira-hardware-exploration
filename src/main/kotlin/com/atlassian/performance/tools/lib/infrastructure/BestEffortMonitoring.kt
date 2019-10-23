package com.atlassian.performance.tools.lib.infrastructure

import com.atlassian.performance.tools.infrastructure.api.process.RemoteMonitoringProcess
import com.atlassian.performance.tools.ssh.api.SshConnection
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

class BestEffortMonitoring(
    private val monitoring: RemoteMonitoringProcess
) : RemoteMonitoringProcess {

    private val logger: Logger = LogManager.getLogger(this::class.java)

    override fun getResultPath(): String {
        return monitoring.getResultPath()
    }

    override fun stop(ssh: SshConnection) {
        try {
            monitoring.stop(ssh)
        } catch (e: Exception) {
            logger.error("Failed to stop $monitoring", e)
        }
    }
}
