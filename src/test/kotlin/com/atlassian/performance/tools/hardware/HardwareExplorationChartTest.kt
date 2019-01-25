package com.atlassian.performance.tools.hardware

import com.atlassian.performance.tools.lib.LogConfigurationFactory
import com.atlassian.performance.tools.workspace.api.RootWorkspace
import com.atlassian.performance.tools.workspace.api.git.HardcodedGitRepo
import org.apache.logging.log4j.core.config.ConfigurationFactory
import org.junit.Test
import java.io.File
import java.nio.file.Paths

class HardwareExplorationChartTest {

    private val workspace = RootWorkspace(Paths.get("build/jpt-workspace")).isolateTask(this::class.java.simpleName)

    @Test
    fun shouldPlot() {
        ConfigurationFactory.setConfigurationFactory(LogConfigurationFactory(workspace))
        val chart = HardwareExplorationChart(HardcodedGitRepo("5fcd73c04783561c3ad672101ac8f759de00dea7"))
        val cachePath = File(javaClass.getResource("/8-node-exploration-cache.json").toURI()).toPath()
        val results = HardwareExplorationResultCache(cachePath).read()

        chart.plot(
            results = results,
            application = "test",
            output = workspace.isolateReport("hardware-exploration-chart.html"),
            instanceTypeOrder = compareBy { it.ordinal }
        )
    }
}