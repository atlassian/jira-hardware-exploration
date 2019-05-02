package com.atlassian.performance.tools.hardware.vu

import com.atlassian.performance.tools.jiraactions.api.SeededRandom
import com.atlassian.performance.tools.jiraactions.api.WebJira
import com.atlassian.performance.tools.jiraactions.api.action.*
import com.atlassian.performance.tools.jiraactions.api.measure.ActionMeter
import com.atlassian.performance.tools.jiraactions.api.memories.JqlMemory
import com.atlassian.performance.tools.jiraactions.api.memories.adaptive.AdaptiveIssueKeyMemory
import com.atlassian.performance.tools.jiraactions.api.memories.adaptive.AdaptiveIssueMemory
import com.atlassian.performance.tools.jiraactions.api.memories.adaptive.AdaptiveJqlMemory
import com.atlassian.performance.tools.jiraactions.api.memories.adaptive.AdaptiveProjectMemory
import com.atlassian.performance.tools.jiraactions.api.memories.adaptive.jql.JqlPrescriptions
import com.atlassian.performance.tools.jiraactions.api.page.IssuePage
import com.atlassian.performance.tools.jiraactions.api.scenario.Scenario
import com.atlassian.performance.tools.jiraactions.api.scenario.addMultiple
import com.atlassian.performance.tools.jirasoftwareactions.api.WebJiraSoftware
import com.atlassian.performance.tools.jirasoftwareactions.api.actions.BrowseBoardsAction
import com.atlassian.performance.tools.jirasoftwareactions.api.actions.ViewBacklogAction
import com.atlassian.performance.tools.jirasoftwareactions.api.actions.ViewBoardAction
import com.atlassian.performance.tools.jirasoftwareactions.api.boards.AgileBoard
import com.atlassian.performance.tools.jirasoftwareactions.api.boards.ScrumBoard
import com.atlassian.performance.tools.jirasoftwareactions.api.memories.AdaptiveBoardMemory
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

class ExtraLargeJiraSoftwareScenario : Scenario{

    override fun getActions(
        jira: WebJira,
        seededRandom: SeededRandom,
        meter: ActionMeter
    ): List<Action> {
        val projectMemory = AdaptiveProjectMemory(random = seededRandom)
        val jqlMemory = AdaptiveJqlMemory(seededRandom)
//        val jqlMemory = MyAdaptiveJqlMemory(seededRandom)
        val issueKeyMemory = AdaptiveIssueKeyMemory(seededRandom)
        val issueMemory = AdaptiveIssueMemory(issueKeyMemory, seededRandom)
        val agileBoardMemory = AdaptiveBoardMemory<AgileBoard>(seededRandom)
        val scrumBoardMemory = AdaptiveBoardMemory<ScrumBoard>(seededRandom)
        val scenario: MutableList<Action> = mutableListOf()
        val createIssue = CreateIssueAction(
            jira = jira,
            meter = meter,
            seededRandom = seededRandom,
            projectMemory = projectMemory
        )
        val searchWithJql = SearchJqlAction(
            jira = jira,
            meter = meter,
            jqlMemory = jqlMemory,
            issueKeyMemory = issueKeyMemory
        )
        val viewIssue = ViewIssueAction(
            jira = jira,
            meter = meter,
            issueKeyMemory = issueKeyMemory,
            issueMemory = issueMemory,
            jqlMemory = jqlMemory
        )
        val projectSummary = ProjectSummaryAction(
            jira = jira,
            meter = meter,
            projectMemory = projectMemory
        )
        val viewDashboard = ViewDashboardAction(
            jira = jira,
            meter = meter
        )
        val editIssue = EditIssueAction(
            jira = jira,
            meter = meter,
            issueMemory = issueMemory
        )
        val addComment = AddCommentAction(
            jira = jira,
            meter = meter,
            issueMemory = issueMemory
        )
        val browseProjects = BrowseProjectsAction(
            jira = jira,
            meter = meter,
            projectMemory = projectMemory
        )

        val jiraSoftware = WebJiraSoftware(jira)
        val viewBoard = ViewBoardAction(
            jiraSoftware = jiraSoftware,
            meter = meter,
            boardMemory = agileBoardMemory,
            issueKeyMemory = issueKeyMemory
        ) { it.issuesOnBoard != 0 }
        val browseBoards = BrowseBoardsAction(
            jiraSoftware = jiraSoftware,
            meter = meter,
            boardsMemory = agileBoardMemory,
            scrumBoardsMemory = scrumBoardMemory
        )
        val viewBacklog = ViewBacklogAction(
            jiraSoftware = jiraSoftware,
            meter = meter,
            boardMemory = scrumBoardMemory
        ) { it.issuesInBacklog != 0 }
        val actionProportions = mapOf(
            createIssue to 25, //5
            searchWithJql to 20,
            viewIssue to 55,
            projectSummary to 5,
            viewDashboard to 10,
            editIssue to 25, //5
            addComment to 30, //2
            browseProjects to 5,
            viewBoard to 10,
            viewBacklog to 10,
            browseBoards to 2
        )
        actionProportions.entries.forEach { scenario.addMultiple(element = it.key, repeats = it.value) }
        scenario.shuffle(seededRandom.random)
        return scenario
    }

}

class MyAdaptiveJqlMemory(
    private val random: SeededRandom
) : JqlMemory {

    private val logger: Logger = LogManager.getLogger(this::class.java)

    private val jqls = mutableListOf(
        "resolved is not empty order by description",
        "text ~ \"a*\" order by summary"
    )

    private val issueBasedJqls = mutableListOf<String>()

    private val jqlPrescriptions = mutableSetOf(
        JqlPrescriptions.prioritiesInEnumeratedList(random),
        JqlPrescriptions.specifiedProject,
        JqlPrescriptions.specifiedAssignee,
        JqlPrescriptions.previousReporters,
        JqlPrescriptions.specifiedAssigneeInSpecifiedProject,
        JqlPrescriptions.filteredByGivenWord(random)
    )

    override fun observe(issuePage: IssuePage) {
        val bakedJql = jqlPrescriptions.asSequence()
            .map { MyBakedJql(it, it(issuePage)) }
            .filter { it.jql != null }
            .firstOrNull()

        bakedJql?.let {
            logger.debug("Rendered a new jql query: <<${it.jql!!}>>")
            if(!issueBasedJqls.contains(it.jql)){
                issueBasedJqls.add(it.jql)
            }
            //jqls.add(it.jql)
            //jqlPrescriptions.remove(it.jqlPrescription)
        }
    }

    override fun recall(): String? {
        return when (issueBasedJqls.size){
            0 -> random.pick(jqls)
            else -> random.pick(issueBasedJqls)
        }
    }

    override fun remember(memories: Collection<String>) {
        jqls.addAll(memories)
    }
}

typealias MyJqlPrescription = (IssuePage) -> String?

data class MyBakedJql(
    val jqlPrescription: MyJqlPrescription,
    val jql: String?
)

