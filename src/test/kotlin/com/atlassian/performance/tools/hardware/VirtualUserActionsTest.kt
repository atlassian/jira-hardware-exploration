package com.atlassian.performance.tools.hardware

import com.atlassian.performance.tools.aws.api.Investment
import com.atlassian.performance.tools.awsinfrastructure.api.InfrastructureFormula
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.C5NineExtraLargeEphemeral
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.M5ExtraLargeEphemeral
import com.atlassian.performance.tools.awsinfrastructure.api.jira.StandaloneFormula
import com.atlassian.performance.tools.awsinfrastructure.api.virtualusers.AbsentVirtualUsersFormula
import com.atlassian.performance.tools.infrastructure.api.jira.JiraNodeConfig
import com.atlassian.performance.tools.infrastructure.api.jvm.EnabledJvmDebug
import com.atlassian.performance.tools.jiraactions.api.SeededRandom
import com.atlassian.performance.tools.jiraactions.api.WebJira
import com.atlassian.performance.tools.jiraactions.api.action.*
import com.atlassian.performance.tools.jiraactions.api.measure.ActionMeter
import com.atlassian.performance.tools.jiraactions.api.measure.output.ThrowawayActionMetricOutput
import com.atlassian.performance.tools.jiraactions.api.memories.Issue
import com.atlassian.performance.tools.jiraactions.api.memories.Project
import com.atlassian.performance.tools.jiraactions.api.memories.User
import com.atlassian.performance.tools.jiraactions.api.memories.adaptive.*
import com.atlassian.performance.tools.jiraactions.api.w3c.DisabledW3cPerformanceTimeline
import com.atlassian.performance.tools.jiraperformancetests.api.VisitableJira
import com.atlassian.performance.tools.jiraperformancetests.api.VisitableJiraRegistry
import com.atlassian.performance.tools.jirasoftwareactions.api.WebJiraSoftware
import com.atlassian.performance.tools.jirasoftwareactions.api.actions.BrowseBoardsAction
import com.atlassian.performance.tools.jirasoftwareactions.api.memories.AdaptiveBoardIdMemory
import com.atlassian.performance.tools.virtualusers.api.browsers.CloseableRemoteWebDriver
import com.atlassian.performance.tools.virtualusers.api.browsers.GoogleChrome
import com.atlassian.performance.tools.virtualusers.api.diagnostics.WebDriverDiagnostics
import com.atlassian.performance.tools.workspace.api.RootWorkspace
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.junit.Test
import java.net.URI
import java.time.Clock
import java.time.Duration
import java.util.*

/**
 * A quick single-action integration test, aimed at quick devloop while developing VU actions or page objects.
 */
class VirtualUserActionsTest {

    private val logger: Logger = LogManager.getLogger(this::class.java)
    private val random = SeededRandom()
    private val root = RootWorkspace()
    private val registry = VisitableJiraRegistry(root)
    private val userMemory = AdaptiveUserMemory(random)
    private val projectMemory = AdaptiveProjectMemory(random)
    private val boardIdMemory = AdaptiveBoardIdMemory(random)
    private val jqlMemory = AdaptiveJqlMemory(random)
    private val issueKeyMemory = AdaptiveIssueKeyMemory(random)
    private val issueMemory = AdaptiveIssueMemory(issueKeyMemory, random)

    @Test
    fun shouldRunVirtualUserActions() {
        registry.memorize(VisitableJira(address = URI("http://63.35.250.149:8080/")))
        (tryStartingChrome() ?: return).use { chrome ->
            val diagnostics = WebDriverDiagnostics(chrome.getDriver())
            try {
                val jira = registry.recall()!!
                logger.info("Using $jira to test the virtual user...")
                val webJira = WebJira(
                    driver = chrome.getDriver(),
                    base = jira.address,
                    adminPassword = "admin"
                )
                val actionMeter = ActionMeter(
                    virtualUser = UUID.randomUUID(),
                    output = ThrowawayActionMetricOutput(),
                    clock = Clock.systemUTC(),
                    w3cPerformanceTimeline = DisabledW3cPerformanceTimeline()
                )
                userMemory.remember(listOf(User("admin", "admin")))
                LogInAction(webJira, actionMeter, userMemory).run()
                SetUpAction(webJira, actionMeter).run()
                exploreJiraData(webJira, actionMeter)
                testMyAction(webJira, actionMeter)
            } catch (e: Exception) {
                diagnostics.diagnose(e)
                throw e
            }
        }
    }



    private fun tryStartingChrome(): CloseableRemoteWebDriver? {
        return try {
            GoogleChrome().start()
        } catch (e: Exception) {
            val ticket = URI("https://bulldog.internal.atlassian.com/browse/JPT-290")
            logger.info("Failed to start Google Chrome, skipping the VU test. If it impacts you, tell us in $ticket", e)
            null
        }
    }

    /**
     * Explores Jira to gather some data for memory-reliant actions.
     */
    private fun exploreJiraData(
        webJira: WebJira,
        actionMeter: ActionMeter
    ) {
        BrowseProjectsAction(webJira, actionMeter, projectMemory).run()
        val webJiraSoftware = WebJiraSoftware(webJira)
        BrowseBoardsAction(webJiraSoftware, actionMeter, boardIdMemory).run()
        SearchJqlAction(webJira, actionMeter, jqlMemory, issueKeyMemory).run()
    }

    /**
     * Tests your specific VU action. Feel free to adapt it to your needs.
     */
    private fun testMyAction(
        webJira: WebJira,
        actionMeter: ActionMeter
    ) {
        repeat(12) {
            val myAction = ViewIssueAction(
                jira = webJira,
                meter = actionMeter,
                issueKeyMemory = issueKeyMemory,
                issueMemory = issueMemory,
                jqlMemory = jqlMemory
            )
            myAction.run()
        }

        projectMemory.remember(listOf(Project("BNGRA", "bnigrita")))
        CreateIssueAction(
            jira = webJira,
            meter = actionMeter,
            projectMemory = projectMemory,
            seededRandom = this.random
        ).run()

        issueKeyMemory.remember(listOf("BNGRA-38311"))
        issueMemory.remember(listOf(Issue("BNGRA-38311", 1010200, "Sub-task", true)))
        EditIssueAction(
            jira = webJira,
            meter = actionMeter,
            issueMemory = issueMemory
        ).run()
    }
}
