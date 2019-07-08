package com.atlassian.performance.tools.lib.infrastructure

import com.atlassian.performance.tools.infrastructure.api.jira.JiraHomeSource
import com.atlassian.performance.tools.ssh.api.SshConnection

class AppNukingJiraHome(
    private val delegate: JiraHomeSource
) : JiraHomeSource {

    override fun download(ssh: SshConnection): String {
        val remotePath = delegate.download(ssh)
        ssh.execute("rm -rf $remotePath/plugins/installed-plugins")
        return remotePath
    }
}
