package com.atlassian.performance.tools.hardware

import com.atlassian.performance.tools.jiraactions.api.SeededRandom
import com.atlassian.performance.tools.jiraactions.api.WebJira
import com.atlassian.performance.tools.jiraactions.api.action.BrowseProjectsAction
import com.atlassian.performance.tools.jiraactions.api.action.LogInAction
import com.atlassian.performance.tools.jiraactions.api.action.SearchJqlAction
import com.atlassian.performance.tools.jiraactions.api.action.ViewIssueAction
import com.atlassian.performance.tools.jiraactions.api.measure.ActionMeter
import com.atlassian.performance.tools.jiraactions.api.memories.User
import com.atlassian.performance.tools.jiraactions.api.memories.adaptive.*
import com.atlassian.performance.tools.jiraperformancetests.api.VisitableJira
import com.atlassian.performance.tools.jiraperformancetests.api.VisitableJiraRegistry
import com.atlassian.performance.tools.jirasoftwareactions.api.WebJiraSoftware
import com.atlassian.performance.tools.jirasoftwareactions.api.actions.BrowseBoardsAction
import com.atlassian.performance.tools.jirasoftwareactions.api.memories.AdaptiveBoardIdMemory
import com.atlassian.performance.tools.virtualusers.api.browsers.CloseableRemoteWebDriver
import com.atlassian.performance.tools.virtualusers.api.browsers.GoogleChrome
import com.atlassian.performance.tools.virtualusers.api.browsers.HeadlessChromeBrowser
import com.atlassian.performance.tools.virtualusers.api.diagnostics.WebDriverDiagnostics
import com.atlassian.performance.tools.workspace.api.RootWorkspace
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.junit.Test
import org.junit.experimental.categories.Category
import java.net.URI
import java.util.*


/**
 * A quick single-action integration test, aimed at quick devloop while developing VU actions or page objects.
 */
class VirtualUserTest {

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

    @Category(VirtualUserTest::class)
    @Test
    fun shouldRunVirtualUserActions() {

        val jira = VisitableJira(URI.create("http://10.116.145.228:8080/")) //http://jpt-1b3ee-loadbala-afc48vxqm5ms-1336805464.eu-west-1.elb.amazonaws.com/"))
        registry.memorize(jira)


        (tryStartingChrome() ?: return).use { chrome ->
            val diagnostics = WebDriverDiagnostics(chrome.getDriver())
            try {
                val jira: VisitableJira? = registry.recall()
                if (jira == null) {
                    throw RuntimeException("No JIRA available")
                }

                logger.info("Using ${jira.address} to test the virtual user...")
                val adminPassword = "MasterPassword18"//"MasterPassword18""admin"

                val webJira = WebJira(
                    driver = chrome.getDriver(),
                    base = jira.address,
                    adminPassword = adminPassword

                )
                val actionMeter = ActionMeter(
                    virtualUser = UUID.randomUUID()
                )
                userMemory.remember(listOf(User("admin", adminPassword)))
                LogInAction(webJira, actionMeter, userMemory).run()

//                EditCustomFieldAction(driver = chrome.getDriver(),
//                    jira = webJira,
//                    meter = actionMeter,
//                    customFieldMemory = MyAdaptiveCustomFieldMemory(random)).run()

//                CustomScenario().getActions(jira = webJira, seededRandom = random, meter = actionMeter).forEach {
//                    it.run()
//                }
                //CustomSetup(webJira, actionMeter).run()
                //exploreJiraData(webJira, actionMeter)
                //testMyAction(webJira, actionMeter)

            } catch (e: Exception) {
                diagnostics.diagnose(e)
                throw e
            }
        }
    }

    private fun tryStartingChrome(): CloseableRemoteWebDriver? {
        return try {
            GoogleChrome().start()
//            HeadlessChromeBrowser().start()
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
    }

}
