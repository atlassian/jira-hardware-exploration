package com.atlassian.performance.tools.lib

import com.atlassian.performance.tools.ssh.api.SshConnection

class SshPostgresClient(
    private val dbName: String = "atldb",
    private val dbUser: String = "postgres",
    private val dbPassword: String ="postgres"): SshSqlClient {

    val connectStr = "PGPASSWORD=$dbPassword psql -h 127.0.0.1 -U $dbUser -d $dbName -c"

    override fun runSql(
        ssh: SshConnection,
        sql: String
    ): SshConnection.SshResult {
        val quotedSql = sql.quote('"')
        return ssh.execute("$connectStr $quotedSql")
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

}