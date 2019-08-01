package com.atlassian.performance.tools.lib.infrastructure

import com.atlassian.performance.tools.infrastructure.api.database.Database
import com.atlassian.performance.tools.infrastructure.api.dataset.DatasetPackage
import com.atlassian.performance.tools.infrastructure.api.os.Ubuntu
import com.atlassian.performance.tools.ssh.api.SshConnection
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.net.URI
import java.time.Duration
import java.time.Instant

class ConfigurableMysqlDatabase(
    private val source: DatasetPackage,
    private val extraDockerArgs: List<String>
) : Database {

    private val logger: Logger = LogManager.getLogger(this::class.java)

    private val image = CopiedDockerImage(
        name = "mysql:5.6.42",
        pullTimeout = Duration.ofMinutes(5)
    )
    private val ubuntu = Ubuntu()

    override fun setup(ssh: SshConnection): String {
        val mysqlData = source.download(ssh)
        image.run(
            ssh = ssh,
            parameters = "-p 3306:3306 -v `realpath $mysqlData`:/var/lib/mysql",
            arguments = "--skip-grant-tables ${extraDockerArgs.joinToString(separator = " ")}"
        )
        return mysqlData
    }

    override fun start(jira: URI, ssh: SshConnection) {
        waitForMysql(ssh)
        ssh.execute("""mysql -h 127.0.0.1  -u root -e "UPDATE jiradb.propertystring SET propertyvalue = '$jira' WHERE id IN (select id from jiradb.propertyentry where property_key like '%baseurl%');" """)
    }

    private fun waitForMysql(ssh: SshConnection) {
        ubuntu.install(ssh, listOf("mysql-client"))
        val mysqlStart = Instant.now()
        while (!ssh.safeExecute("mysql -h 127.0.0.1 -u root -e 'select 1;'").isSuccessful()) {
            if (Instant.now() > mysqlStart + Duration.ofMinutes(15)) {
                throw RuntimeException("MySql didn't start in time")
            }
            logger.debug("Waiting for MySQL...")
            Thread.sleep(Duration.ofSeconds(10).toMillis())
        }
    }
}
