package com.atlassian.performance.tools.lib

import com.atlassian.performance.tools.ssh.api.SshConnection
import java.io.File

class SshPostgresClient(
    dbName: String = "atldb",
    dbUser: String = "postgres",
    dbPassword: String = "postgres"
) : SshSqlClient {

    private val psql = "PGPASSWORD=$dbPassword psql -h 127.0.0.1 -U $dbUser -d $dbName"

    override fun runSql(
        ssh: SshConnection,
        sql: String
    ): SshConnection.SshResult {
        val quotedSql = sql.quote('"')
        return ssh.execute("$psql -c $quotedSql")
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
        val remoteSqlFile = sql.name
        ssh.upload(sql, remoteSqlFile)
        val result = ssh.execute("$psql -f $remoteSqlFile")
        ssh.execute("rm $remoteSqlFile")
        return result
    }
}
