package com.atlassian.performance.tools.hardware.vu

import com.atlassian.performance.tools.jiraactions.api.SeededRandom
import com.atlassian.performance.tools.jiraactions.api.WebJira
import com.atlassian.performance.tools.jiraactions.api.action.Action
import com.atlassian.performance.tools.jiraactions.api.measure.ActionMeter
import com.atlassian.performance.tools.jiraactions.api.scenario.Scenario
import com.atlassian.performance.tools.jiraactions.api.w3c.DisabledW3cPerformanceTimeline
import com.atlassian.performance.tools.jirasoftwareactions.api.JiraSoftwareScenario

class CustomScenario : Scenario {

    override fun getSetupAction(
        jira: WebJira,
        meter: ActionMeter
    ): Action {
        return CustomSetup(jira, meter)
    }

    override fun getActions(
        jira: WebJira,
        seededRandom: SeededRandom,
        meter: ActionMeter
    ): List<Action> {
        val noDrilldownMeter = meter.withW3cPerformanceTimeline(
            DisabledW3cPerformanceTimeline()
        )
        return JiraSoftwareScenario().getActions(jira, seededRandom, noDrilldownMeter)
    }
}
