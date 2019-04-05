package com.atlassian.performance.tools.lib

import com.atlassian.performance.tools.infrastructure.api.database.Database
import com.atlassian.performance.tools.infrastructure.api.database.DbType
import com.atlassian.performance.tools.ssh.api.SshConnection
import org.apache.logging.log4j.LogManager
import java.net.URI

internal class LicenseOverridingDatabase(
    private val database: Database,
    private val licenses: List<String>
) : Database {
    override fun getDbType(): DbType {
        return database.getDbType()
    }

    private val logger = LogManager.getLogger(this::class.java)

    override fun setup(
        ssh: SshConnection
    ): String = database.setup(ssh)

    override fun start(
        jira: URI,
        ssh: SshConnection
    ) {
        database.start(jira, ssh)
        val licenseTable =when(database.getDbType())
        {
            DbType.MySql -> "jiradb.productlicense"
            DbType.Postgres -> "productlicense"
        }
        var client : SshSqlClient
        when(database.getDbType()){
            DbType.MySql -> client = SshMysqlClient()
            DbType.Postgres -> client = SshPostgresClient()
            else -> throw Exception("Unknow DB : ${database.getDbType()}")
        }
        client.runSql(ssh, "DELETE FROM $licenseTable;")
        logger.info("Licenses nuked")
        licenses.forEachIndexed { index, license ->
            val flattenedLicense = license.lines().joinToString(separator = "") { it.trim() }

            when(database.getDbType())
            {
                DbType.MySql -> client.runSql(
                    ssh = ssh,
                    sql = "INSERT INTO $licenseTable VALUES ($index, \"$flattenedLicense\");"
                )
                DbType.Postgres -> client.runSql(
                    ssh = ssh,
                    sql = "INSERT INTO $licenseTable (id, license) VALUES ($index, '$flattenedLicense');"
                )
            }
            logger.info("Added license: ${license.substring(0..8)}...")
        }
    }

}
