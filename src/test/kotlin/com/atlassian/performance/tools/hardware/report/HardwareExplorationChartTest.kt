package com.atlassian.performance.tools.hardware.report

import com.amazonaws.services.ec2.model.InstanceType
import com.atlassian.performance.tools.hardware.HardwareExplorationResultCache
import com.atlassian.performance.tools.lib.LogConfigurationFactory
import com.atlassian.performance.tools.workspace.api.RootWorkspace
import com.atlassian.performance.tools.workspace.api.git.HardcodedGitRepo
import org.apache.logging.log4j.core.config.ConfigurationFactory
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Paths

class HardwareExplorationChartTest {

    private val workspace = RootWorkspace(Paths.get("build/jpt-workspace")).isolateTask(this::class.java.simpleName)

    @Before
    fun setUp() {
        ConfigurationFactory.setConfigurationFactory(LogConfigurationFactory(workspace))
    }

    @Test
    fun shouldPlotJiraExploration() {
        val chart = HardwareExplorationChart(
            JiraInstanceTypeGrouping(compareBy { it.ordinal }),
            NodeCountXAxis(),
            HardcodedGitRepo("5fcd73c04783561c3ad672101ac8f759de00dea7")
        )
        val cachePath = File(javaClass.getResource("/8-node-exploration-cache.json").toURI()).toPath()
        val results = HardwareExplorationResultCache(cachePath).read()

        chart.plot(
            results = results,
            application = "test",
            output = workspace.isolateReport("jira-hardware-exploration-chart.html")
        )
    }

    @Test
    fun shouldPlotDbExploration() {
        val chart = HardwareExplorationChart(
            JiraClusterGrouping(InstanceType.values().toList()),
            DbInstanceTypeXAxis(),
            HardcodedGitRepo("fake commit")
        )
        val cachePath = File(javaClass.getResource("/8-node-exploration-cache.json").toURI()).toPath()
        val results = HardwareExplorationResultCache(cachePath).read()

        chart.plot(
            results = results,
            application = "test",
            output = workspace.isolateReport("db-hardware-exploration-chart.html")
        )
    }
}
