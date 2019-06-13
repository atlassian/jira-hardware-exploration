package com.atlassian.performance.tools.lib

import com.atlassian.performance.tools.infrastructure.api.database.Database
import com.atlassian.performance.tools.ssh.api.SshConnection
import org.apache.logging.log4j.LogManager
import java.net.URI

internal class LicenseOverridingDatabase(
    private val database: Database,
    private val licenses: List<String>
) : Database {

    private val logger = LogManager.getLogger(this::class.java)

    override fun setup(
        ssh: SshConnection
    ): String = database.setup(ssh)

    override fun start(
        jira: URI,
        ssh: SshConnection
    ) {
        database.start(jira, ssh)
        val licenseTable = "jiradb.productlicense"
        var client = SshMysqlClient()
        client.runSql(ssh, "DELETE FROM $licenseTable;")
        logger.info("Licenses nuked")
        licenses.forEachIndexed { index, license ->
            val flattenedLicense = license.lines().joinToString(separator = "") { it.trim() }
            client.runSql(
                ssh = ssh,
                sql = "INSERT INTO $licenseTable VALUES ($index, \"$flattenedLicense\");"
            )
            logger.info("Added license: ${license.substring(0..8)}...")
        }
    }

}
