package com.atlassian.performance.tools.lib

import com.atlassian.performance.tools.ssh.api.SshConnection
import java.io.File

class SshMysqlClient : SshSqlClient {

    override fun runSql(
        ssh: SshConnection,
        sql: String
    ): SshConnection.SshResult {
        val quotedSql = sql.quote('"')
        return ssh.execute("mysql -h 127.0.0.1 -u root -e $quotedSql")
    }

    private fun String.quote(
        quote: Char
    ): String = quote + escape(quote) + quote

    private fun String.escape(
        character: Char
    ): String = replace(
        oldValue = character.toString(),
        newValue = "\\$character"
    )

    override fun runSql(
        ssh: SshConnection,
        sql: File
    ): SshConnection.SshResult {
        ssh.upload(sql, sql.name)
        return ssh.execute("mysql -h 127.0.0.1 -u root < ${sql.name}")
    }
}
