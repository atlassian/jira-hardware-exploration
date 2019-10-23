package com.atlassian.performance.tools.lib.infrastructure

import com.atlassian.performance.tools.infrastructure.api.process.RemoteMonitoringProcess
import com.atlassian.performance.tools.infrastructure.api.profiler.Profiler
import com.atlassian.performance.tools.ssh.api.SshConnection
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

class BestEffortProfiler(
    private val profiler: Profiler
) : Profiler {

    private val logger: Logger = LogManager.getLogger(this::class.java)

    override fun install(ssh: SshConnection) {
        try {
            profiler.install(ssh)
        } catch (e: Exception) {
            logger.error("Failed to install $profiler", e)
        }
    }

    override fun start(ssh: SshConnection, pid: Int): RemoteMonitoringProcess? {
        return try {
            profiler.start(ssh, pid)?.let { BestEffortMonitoring(it) }
        } catch (e: Exception) {
            logger.error("Failed to start $profiler for $pid", e)
            null
        }
    }
}