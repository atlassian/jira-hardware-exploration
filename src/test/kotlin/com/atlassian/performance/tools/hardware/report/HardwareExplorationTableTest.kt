package com.atlassian.performance.tools.hardware.report

import com.amazonaws.services.ec2.model.InstanceType
import com.atlassian.performance.tools.hardware.*
import com.atlassian.performance.tools.infrastructure.api.database.Database
import com.atlassian.performance.tools.infrastructure.api.dataset.Dataset
import com.atlassian.performance.tools.infrastructure.api.jira.JiraHomeSource
import com.atlassian.performance.tools.lib.Ratio
import com.atlassian.performance.tools.lib.infrastructure.AdminDataset
import com.atlassian.performance.tools.lib.report.VirtualUsersPresenceJudge
import com.atlassian.performance.tools.report.api.result.RawCohortResult
import com.atlassian.performance.tools.ssh.api.SshConnection
import com.atlassian.performance.tools.virtualusers.api.TemporalRate
import com.atlassian.performance.tools.virtualusers.api.VirtualUserLoad
import com.atlassian.performance.tools.workspace.api.RootWorkspace
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.io.File
import java.net.URI
import java.nio.file.Paths
import java.time.Duration

class HardwareExplorationTableTest {

    private val workspace = RootWorkspace(Paths.get("build")).currentTask.isolateTest(javaClass.simpleName)
    private val dummyDataset = AdminDataset(
        dataset = Dataset(
            FailingDatabase(),
            FailingJiraHome(),
            "dummy"
        ),
        adminLogin = "a",
        adminPassword = "a"
    )

    @Test
    fun shouldSummarize() {
        val hardware = Hardware(
            jira = InstanceType.C59xlarge,
            nodeCount = 2,
            db = InstanceType.M4Xlarge
        )
        val exploration = HardwareExplorationResult(
            decision = HardwareExplorationDecision(
                hardware,
                worthExploring = true,
                reason = "It's a unit test"
            ),
            testResult = HardwareMetric(
                scale = ApplicationScale(
                    description = "JIRA-JPTC-1339",
                    cacheKey = "JIRA-JPTC-1339",
                    dataset = dummyDataset,
                    load = VirtualUserLoad.Builder()
                        .virtualUsers(75)
                        .ramp(Duration.ofSeconds(90))
                        .flat(Duration.ofMinutes(20))
                        .maxOverallLoad(TemporalRate(15.0, Duration.ofSeconds(1)))
                        .build(),
                    vuNodes = 6
                ),
                presenceJudge = VirtualUsersPresenceJudge(Ratio(0.12)), // partial data
                errorRateWarningThreshold = 0.05
            ).score(
                hardware = hardware,
                results = RawCohortResult.Factory().fullResult(
                    cohort = "JIRA-JPTC-1339",
                    results = File(javaClass.getResource("/JIRA-JPTC-1339").toURI()).toPath()
                )
            )
        )
        val table = workspace.directory.resolve("table.csv")

        HardwareExplorationTable().summarize(
            results = listOf(exploration),
            table = table
        )

        assertThat(table.toFile()).hasSameContentAs(
            File(javaClass.getResource("expected-table-1.csv").toURI())
        )
    }
}

class FailingDatabase : Database {

    override fun setup(ssh: SshConnection): String = fail()
    override fun start(jira: URI, ssh: SshConnection): Unit = fail()
}

class FailingJiraHome : JiraHomeSource {
    override fun download(ssh: SshConnection): String = fail()
}

private fun fail(): Nothing = throw Exception("unexpected call")
