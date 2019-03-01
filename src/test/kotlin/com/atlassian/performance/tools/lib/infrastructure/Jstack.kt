package com.atlassian.performance.tools.lib.infrastructure

import com.atlassian.performance.tools.infrastructure.api.process.RemoteMonitoringProcess
import com.atlassian.performance.tools.infrastructure.api.profiler.Profiler
import com.atlassian.performance.tools.ssh.api.DetachedProcess
import com.atlassian.performance.tools.ssh.api.SshConnection
import java.time.Duration

class Jstack(
    private val period: Duration
) : Profiler {

    override fun install(ssh: SshConnection) {
    }

    override fun start(
        ssh: SshConnection,
        pid: Int
    ): RemoteMonitoringProcess? {
        val dumpsPath = "jstack-threaddumps"
        val jstackOnce = "jstack -l $pid > $dumpsPath/$(date +%s)"
        val detached = ssh.startProcess("while true; do $jstackOnce; sleep ${period.seconds}; done")
        return JstackProcess(detached, dumpsPath)
    }

    private class JstackProcess(
        private val detached: DetachedProcess,
        private val resultPath: String
    ) : RemoteMonitoringProcess {

        override fun stop(ssh: SshConnection) {
            ssh.stopProcess(detached)
        }

        override fun getResultPath(): String {
            return resultPath
        }
    }
}
