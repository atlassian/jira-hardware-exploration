package com.atlassian.performance.tools.hardware.report

import com.amazonaws.services.ec2.model.InstanceType
import com.atlassian.performance.tools.hardware.Hardware
import com.atlassian.performance.tools.hardware.HardwareExplorationDecision
import com.atlassian.performance.tools.hardware.HardwareExplorationResult
import com.atlassian.performance.tools.hardware.HardwareMetric
import com.atlassian.performance.tools.report.api.FullTimeline
import com.atlassian.performance.tools.report.api.result.RawCohortResult
import com.atlassian.performance.tools.workspace.api.RootWorkspace
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.io.File
import java.nio.file.Paths

class HardwareExplorationTableTest {

    private val workspace = RootWorkspace(Paths.get("build")).currentTask.isolateTest(javaClass.simpleName)

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
                timeline = FullTimeline(),
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