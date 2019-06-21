package com.atlassian.performance.tools.hardware.report

import com.amazonaws.services.ec2.model.InstanceType
import com.atlassian.performance.tools.hardware.HardwareExplorationResult
import com.atlassian.performance.tools.hardware.HardwareExplorationResultCache
import com.atlassian.performance.tools.hardware.RecommendationSet
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
        val resourcePath = "/8-node-exploration-cache.json"
        val exploration = readExploration(resourcePath)

        chart.plot(
            exploration = exploration,
            application = "test",
            output = workspace.isolateReport("jira-hardware-exploration-chart.html")
        )
    }

    @Test
    fun shouldPlotJiraRecommendation() {
        val chart = HardwareExplorationChart(
            JiraInstanceTypeGrouping(compareBy { it.ordinal }),
            NodeCountXAxis(),
            HardcodedGitRepo("5fcd73c04783561c3ad672101ac8f759de00dea7")
        )
        val recommendations = readRecommendations("/xl-quick-132-cache.json")

        chart.plotRecommendation(
            recommendations = recommendations,
            application = "test",
            output = workspace.isolateReport("jira-hardware-recommendation-chart.html")
        )
    }

    private fun readRecommendations(
        resourcePath: String
    ): RecommendationSet {
        val exploration = readExploration(resourcePath)
        val candidates = exploration
            .mapNotNull { it.testResult }
            .filter { it.apdex > 0.40 }
        return RecommendationSet(
            exploration = exploration,
            bestApdex = candidates.sortedByDescending { it.apdex }.first(),
            bestCostEffectiveness = candidates.sortedByDescending { it.apdexPerUsdUpkeep }.first()
        )
    }

    @Test
    fun shouldPlotDbRecommendation() {
        val chart = HardwareExplorationChart(
            JiraInstanceTypeGrouping(compareBy { it.ordinal }),
            DbInstanceTypeXAxis(),
            HardcodedGitRepo("5fcd73c04783561c3ad672101ac8f759de00dea7")
        )
        val recommendations = readRecommendations("/xl-quick-132-cache-db.json")

        chart.plotRecommendation(
            recommendations = recommendations,
            application = "test",
            output = workspace.isolateReport("db-hardware-recommendation-chart.html")
        )
    }

    @Test
    fun shouldPlotDbExploration() {
        val chart = HardwareExplorationChart(
            JiraClusterGrouping(InstanceType.values().toList()),
            DbInstanceTypeXAxis(),
            HardcodedGitRepo("fake commit")
        )
        val exploration = readExploration("/8-node-exploration-cache.json")

        chart.plot(
            exploration = exploration,
            application = "test",
            output = workspace.isolateReport("db-hardware-exploration-chart.html")
        )
    }

    @Test
    fun shouldKeepErrorBarsWithinTheChart() {
        val chart = HardwareExplorationChart(
            JiraInstanceTypeGrouping(compareBy { it.ordinal }),
            NodeCountXAxis(),
            HardcodedGitRepo("whatevs")
        )
        val exploration = readExploration("/out-of-bounds-error-bars-cache.json")

        chart.plot(
            exploration = exploration,
            application = "error bar test",
            output = workspace.isolateReport("error-bars-test.html")
        )
    }

    private fun readExploration(
        resourcePath: String
    ): List<HardwareExplorationResult> = resourcePath
        .let { javaClass.getResource(it) }
        .toURI()
        .let { File(it) }
        .toPath()
        .let { HardwareExplorationResultCache(it) }
        .read()
}
