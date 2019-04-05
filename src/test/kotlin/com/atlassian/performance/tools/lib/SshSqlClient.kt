package com.atlassian.performance.tools.lib

import com.atlassian.performance.tools.ssh.api.SshConnection

interface SshSqlClient{

    fun runSql(
        ssh: SshConnection,
        sql: String
    ): SshConnection.SshResult

}