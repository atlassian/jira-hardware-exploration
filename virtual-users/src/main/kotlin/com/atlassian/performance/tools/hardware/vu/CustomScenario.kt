package com.atlassian.performance.tools.hardware.vu

import com.atlassian.performance.tools.jiraactions.api.SeededRandom
import com.atlassian.performance.tools.jiraactions.api.WebJira
import com.atlassian.performance.tools.jiraactions.api.action.*
import com.atlassian.performance.tools.jiraactions.api.measure.ActionMeter
import com.atlassian.performance.tools.jiraactions.api.memories.adaptive.AdaptiveIssueKeyMemory
import com.atlassian.performance.tools.jiraactions.api.memories.adaptive.AdaptiveIssueMemory
import com.atlassian.performance.tools.jiraactions.api.memories.adaptive.AdaptiveJqlMemory
import com.atlassian.performance.tools.jiraactions.api.memories.adaptive.AdaptiveProjectMemory
import com.atlassian.performance.tools.jiraactions.api.scenario.Scenario
import com.atlassian.performance.tools.jiraactions.api.scenario.addMultiple
import com.atlassian.performance.tools.jiraactions.api.w3c.JavascriptW3cPerformanceTimeline
import com.atlassian.performance.tools.jirasoftwareactions.api.WebJiraSoftware
import com.atlassian.performance.tools.jirasoftwareactions.api.actions.ViewBacklogAction
import com.atlassian.performance.tools.jirasoftwareactions.api.actions.ViewBoardAction
import com.atlassian.performance.tools.jirasoftwareactions.api.boards.AgileBoard
import com.atlassian.performance.tools.jirasoftwareactions.api.boards.ScrumBoard
import com.atlassian.performance.tools.jirasoftwareactions.api.memories.AdaptiveBoardMemory
import org.openqa.selenium.JavascriptExecutor

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
        val drilldownMeter = meter.withW3cPerformanceTimeline(
            JavascriptW3cPerformanceTimeline(jira.driver as JavascriptExecutor)
        )
        val projectMemory = AdaptiveProjectMemory(random = seededRandom)
        val jqlMemory = AdaptiveJqlMemory(seededRandom)
        val issueKeyMemory = AdaptiveIssueKeyMemory(seededRandom)
        val issueMemory = AdaptiveIssueMemory(issueKeyMemory, seededRandom)
        val agileBoardMemory = AdaptiveBoardMemory<AgileBoard>(seededRandom)
        val scrumBoardMemory = AdaptiveBoardMemory<ScrumBoard>(seededRandom)
        val scenario: MutableList<Action> = mutableListOf()
        val createIssue = CreateIssueAction(
            jira = jira,
            meter = drilldownMeter,
            seededRandom = seededRandom,
            projectMemory = projectMemory
        )
        val searchWithJql = SearchJqlAction(
            jira = jira,
            meter = drilldownMeter,
            jqlMemory = jqlMemory,
            issueKeyMemory = issueKeyMemory
        )
        val viewIssue = ViewIssueAction(
            jira = jira,
            meter = drilldownMeter,
            issueKeyMemory = issueKeyMemory,
            issueMemory = issueMemory,
            jqlMemory = jqlMemory
        )
        val projectSummary = ProjectSummaryAction(
            jira = jira,
            meter = drilldownMeter,
            projectMemory = projectMemory
        )
        val viewDashboard = ViewDashboardAction(
            jira = jira,
            meter = drilldownMeter
        )
        val editIssue = EditIssueAction(
            jira = jira,
            meter = drilldownMeter,
            issueMemory = issueMemory
        )
        val addComment = AddCommentAction(
            jira = jira,
            meter = drilldownMeter,
            issueMemory = issueMemory
        )
        val browseProjects = BrowseProjectsAction(
            jira = jira,
            meter = drilldownMeter,
            projectMemory = projectMemory
        )

        val jiraSoftware = WebJiraSoftware(jira)
        val viewBoard = ViewBoardAction(
            jiraSoftware = jiraSoftware,
            meter = drilldownMeter,
            boardMemory = agileBoardMemory,
            issueKeyMemory = issueKeyMemory
        ) { it.issuesOnBoard != 0 }
        val browseBoards = PatientBrowseBoardsAction(
            driver = jira.driver,
            jiraSoftware = WebJiraSoftware(jira),
            meter = meter,
            boardsMemory = agileBoardMemory,
            scrumBoardsMemory = scrumBoardMemory
        )
        val viewBacklog = ViewBacklogAction(
            jiraSoftware = jiraSoftware,
            meter = drilldownMeter,
            boardMemory = scrumBoardMemory
        ) { it.issuesInBacklog != 0 }
        val actionProportions = mapOf(
            createIssue to 5,
            searchWithJql to 20,
            viewIssue to 55,
            projectSummary to 5,
            viewDashboard to 10,
            editIssue to 5,
            addComment to 2,
            browseProjects to 5,
            viewBoard to 10,
            viewBacklog to 10,
            browseBoards to 2
        )
        actionProportions
            .entries
            .forEach { (action, repeats) -> scenario.addMultiple(repeats, action) }
        scenario.shuffle(seededRandom.random)
        return scenario
    }
}
