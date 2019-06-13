package com.atlassian.performance.tools.lib

import com.atlassian.performance.tools.infrastructure.api.database.Database
import com.atlassian.performance.tools.infrastructure.api.database.DbType
import com.atlassian.performance.tools.infrastructure.api.database.DbType.MySql
import com.atlassian.performance.tools.infrastructure.api.database.DbType.Postgres
import com.atlassian.performance.tools.ssh.api.SshConnection
import org.apache.logging.log4j.LogManager
import java.net.URI

internal class LicenseOverridingDatabase(
    private val database: Database,
    private val licenses: List<String>
) : Database {

    private val logger = LogManager.getLogger(this::class.java)

    override fun getDbType(): DbType {
        return database.getDbType()
    }

    override fun setup(
        ssh: SshConnection
    ): String = database.setup(ssh)

    override fun start(
        jira: URI,
        ssh: SshConnection
    ) {
        database.start(jira, ssh)
        val dbType = getDbType()
        val licenseTable = when (dbType) {
            MySql -> "jiradb.productlicense"
            Postgres -> "productlicense"
        }
        val client = when (dbType) {
            MySql -> SshMysqlClient()
            Postgres -> SshPostgresClient()
        }
        client.runSql(ssh, "DELETE FROM $licenseTable;")
        logger.info("Licenses nuked")
        licenses.forEachIndexed { index, license ->
            val flattenedLicense = license.lines().joinToString(separator = "") { it.trim() }
            val insert = when (dbType) {
                MySql -> "INSERT INTO $licenseTable VALUES ($index, \"$flattenedLicense\");"
                Postgres -> "INSERT INTO $licenseTable (id, license) VALUES ($index, '$flattenedLicense');"
            }
            client.runSql(ssh, insert)
            logger.info("Added license: ${license.substring(0..8)}...")
        }
    }
}
