package com.atlassian.performance.tools.hardware.report

import com.amazonaws.services.ec2.model.InstanceType
import com.atlassian.performance.tools.hardware.MockResult
import com.atlassian.performance.tools.hardware.OutcomeRequirements
import com.atlassian.performance.tools.lib.LogConfigurationFactory
import com.atlassian.performance.tools.lib.OverallError
import com.atlassian.performance.tools.lib.Ratio
import com.atlassian.performance.tools.workspace.api.RootWorkspace
import com.atlassian.performance.tools.workspace.api.git.HardcodedGitRepo
import org.apache.logging.log4j.core.config.ConfigurationFactory
import org.junit.Before
import org.junit.Test
import java.nio.file.Paths

class HardwareExplorationChartTest {

    private val workspace = RootWorkspace(Paths.get("build/jpt-workspace")).isolateTask(this::class.java.simpleName)
    private val requirements = OutcomeRequirements(
        apdexThreshold = 0.70,
        overallErrorThreshold = OverallError(Ratio(0.01)),
        maxActionErrorThreshold = Ratio(0.05)
    )

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
        val exploration = MockResult("/8-node-exploration-cache.json").readExploration()

        chart.plot(
            exploration = exploration,
            requirements = requirements,
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
        val recommendations = MockResult("/xl-quick-132-cache.json").readRecommendations()

        chart.plotRecommendation(
            recommendations = recommendations,
            application = "test",
            output = workspace.isolateReport("jira-hardware-recommendation-chart.html")
        )
    }

    @Test
    fun shouldPlotDbRecommendation() {
        val chart = HardwareExplorationChart(
            JiraClusterGrouping(InstanceType.values().toList()),
            DbInstanceTypeXAxis(),
            HardcodedGitRepo("5fcd73c04783561c3ad672101ac8f759de00dea7")
        )
        val recommendations = MockResult("/xl-quick-132-cache-db.json").readRecommendations()

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
        val exploration = MockResult("/8-node-exploration-cache.json").readExploration()

        chart.plot(
            exploration = exploration,
            requirements = requirements,
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
        val exploration = MockResult("/out-of-bounds-error-bars-cache.json").readExploration()

        chart.plot(
            exploration = exploration,
            requirements = requirements,
            application = "error bar test",
            output = workspace.isolateReport("error-bars-test.html")
        )
    }

}
