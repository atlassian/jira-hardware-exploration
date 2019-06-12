package com.atlassian.performance.tools.lib.awsinfrastructure

import com.atlassian.performance.tools.aws.api.*
import com.atlassian.performance.tools.awsinfrastructure.api.Infrastructure
import com.atlassian.performance.tools.awsinfrastructure.api.InfrastructureFormula
import com.atlassian.performance.tools.awsinfrastructure.api.RemoteLocation
import com.atlassian.performance.tools.awsinfrastructure.api.jira.Jira
import com.atlassian.performance.tools.infrastructure.api.virtualusers.MulticastVirtualUsers
import com.atlassian.performance.tools.infrastructure.api.virtualusers.ResultsTransport
import com.atlassian.performance.tools.infrastructure.api.virtualusers.SshVirtualUsers
import com.atlassian.performance.tools.io.api.ensureParentDirectory
import com.atlassian.performance.tools.lib.toExistingFile
import com.atlassian.performance.tools.ssh.api.Ssh
import com.atlassian.performance.tools.ssh.api.SshHost
import java.net.URI
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import javax.json.JsonArray
import javax.json.JsonObject
import javax.json.JsonString
import javax.json.JsonValue
import javax.json.spi.JsonProvider

class CacheableInfrastructure(
    private val formula: InfrastructureFormula<MulticastVirtualUsers<SshVirtualUsers>>,
    private val aws: Aws,
    private val cache: Path,
    private val workspace: Path
) {

    private val jsonp: JsonProvider = JsonProvider.provider()

    fun obtain(): Infrastructure<*> {
        val cached = read()
        if (cached != null) {
            return cached
        }
        val provisioned = formula.provision(workspace).infrastructure
        write(provisioned)
        return provisioned
    }

    private fun write(
        infrastructure: Infrastructure<MulticastVirtualUsers<SshVirtualUsers>>
    ) {
        val json = writeInfra(infrastructure)
        cache
            .toFile()
            .ensureParentDirectory()
            .bufferedWriter()
            .use { bufferedWriter ->
                jsonp.createWriter(bufferedWriter).use { jsonWriter ->
                    jsonWriter.write(json)
                }
            }
    }

    private fun read(): Infrastructure<*>? {
        return cache
            .toExistingFile()
            ?.bufferedReader()
            ?.use { bufferedReader ->
                jsonp.createReader(bufferedReader).use { jsonReader ->
                    jsonReader.readObject()
                }
            }
            ?.let { readInfra(it) }
    }

    private fun writeInfra(
        infrastructure: Infrastructure<MulticastVirtualUsers<SshVirtualUsers>>
    ): JsonValue = jsonp
        .createObjectBuilder()
        .add("jira", writeJira(infrastructure.jira))
        .add("virtualUsers", writeMulticastVus(infrastructure.virtualUsers))
        .add("sshKey", writeSshKey(infrastructure.sshKey))
        .build()

    private fun readInfra(
        json: JsonObject
    ): Infrastructure<*> {
        val resultsStorage = aws.resultsStorage(UUID.randomUUID().toString())
        return Infrastructure(
            jira = readJira(json.getJsonObject("jira")),
            virtualUsers = readMulticastVus(
                json.getJsonArray("virtualUsers"),
                CopiedS3ResultsTransport(resultsStorage)
            ),
            sshKey = readSshKey(json.getJsonString("sshKey")),
            resultsTransport = resultsStorage
        )
    }

    private fun readMulticastVus(
        vuArray: JsonArray,
        resultsTransport: ResultsTransport
    ): MulticastVirtualUsers<*> {
        val vuNodes = vuArray.mapIndexed { index, vuJson ->
            SshVirtualUsers(
                name = "reused-vu-$index",
                nodeOrder = index,
                resultsTransport = resultsTransport,
                ssh = Ssh(
                    host = SshHost(vuJson.asJsonObject().getJsonObject("ssh"))
                ),
                jarName = "virtual-users.jar"
            )
        }
        return MulticastVirtualUsers(vuNodes)
    }

    private fun writeMulticastVus(
        virtualUsers: MulticastVirtualUsers<SshVirtualUsers>
    ): JsonValue {
        val array = jsonp.createArrayBuilder()
        virtualUsers
            .nodes
            .map { writeSshVu(it) }
            .forEach { array.add(it) }
        return array.build()
    }

    private fun writeSshVu(
        virtualUsers: SshVirtualUsers
    ): JsonValue = jsonp
        .createObjectBuilder()
        .add("ssh", virtualUsers.ssh.host.toJson())
        .build()

    private fun readJira(
        json: JsonObject
    ): Jira = Jira(
        nodes = emptyList(),
        address = URI(json.getString("address")),
        jiraHome = RemoteLocation(json.getJsonObject("jiraHome")),
        database = json.getJsonObject("database")?.let { RemoteLocation(it) }
    )

    private fun writeJira(
        jira: Jira
    ): JsonValue = jsonp
        .createObjectBuilder()
        .add("address", jira.address.toString())
        .add("jiraHome", jira.jiraHome.toJson())
        .apply { jira.database?.let { add("database", it.toJson()) } }
        .build()

    private fun writeSshKey(
        sshKey: SshKey
    ): JsonValue = sshKey
        .file
        .path
        .toString()
        .let { jsonp.createValue(it) }

    private fun readSshKey(
        json: JsonString
    ): SshKey = SshKey(
        SshKeyFile(Paths.get(json.string)),
        RemoteSshKey(
            SshKeyName("shouldn't matter"),
            aws.ec2
        )
    )
}
