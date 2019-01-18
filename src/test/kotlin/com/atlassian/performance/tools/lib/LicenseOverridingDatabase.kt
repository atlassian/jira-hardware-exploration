package com.atlassian.performance.tools.lib

import com.atlassian.performance.tools.infrastructure.api.database.Database
import com.atlassian.performance.tools.ssh.api.SshConnection
import java.net.URI

internal class LicenseOverridingDatabase(
    private val database: Database,
    private val licenses: List<String>
) : Database {
    override fun setup(
        ssh: SshConnection
    ): String = database.setup(ssh)

    override fun start(
        jira: URI,
        ssh: SshConnection
    ) {
        database.start(jira, ssh)
        val firstLicense = licenses.first()
        val licenseTable = "jiradb.productlicense"
        val sql = "DELETE FROM $licenseTable; REPLACE INTO $licenseTable (LICENSE) VALUES (\"$firstLicense\");"
        val mysql = SshMysqlClient()
        mysql.runSql(ssh, sql)
        licenses.drop(1).forEach { license ->
            mysql.runSql(
                ssh = ssh,
                sql = "INSERT INTO $licenseTable SELECT MAX(id)+1, \"$license\" FROM $licenseTable;"
            )
        }
    }
}
