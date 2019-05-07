package com.atlassian.performance.tools.hardware.vu

import com.atlassian.performance.tools.jiraactions.api.BROWSE_BOARDS
import com.atlassian.performance.tools.jiraactions.api.action.Action
import com.atlassian.performance.tools.jiraactions.api.measure.ActionMeter
import com.atlassian.performance.tools.jiraactions.api.page.JiraErrors
import com.atlassian.performance.tools.jiraactions.api.page.wait
import com.atlassian.performance.tools.jirasoftwareactions.api.WebJiraSoftware
import com.atlassian.performance.tools.jirasoftwareactions.api.boards.AgileBoard
import com.atlassian.performance.tools.jirasoftwareactions.api.boards.ScrumBoard
import com.atlassian.performance.tools.jirasoftwareactions.api.memories.BoardMemory
import com.atlassian.performance.tools.jirasoftwareactions.api.page.BrowseBoardsPage
import net.jcip.annotations.NotThreadSafe
import org.openqa.selenium.By
import org.openqa.selenium.WebDriver
import org.openqa.selenium.support.ui.ExpectedConditions
import java.time.Duration

@NotThreadSafe
class PatientBrowseBoardsAction(
    private val driver: WebDriver,
    private val jiraSoftware: WebJiraSoftware,
    private val meter: ActionMeter,
    private val boardsMemory: BoardMemory<AgileBoard>,
    private val scrumBoardsMemory: BoardMemory<ScrumBoard>
) : Action {

    override fun run() {
        val browseBoardsPage = meter.measure(BROWSE_BOARDS) {
            jiraSoftware.goToBrowseBoards().waitForBoardsList(Duration.ofMinutes(10))
        }
        boardsMemory.remember(browseBoardsPage.getBoardIds().map { AgileBoard(it) })
        scrumBoardsMemory.remember(browseBoardsPage.getScrumBoardIds().map { ScrumBoard(it) })
    }

    private fun BrowseBoardsPage.waitForBoardsList(
        patience: Duration
    ): BrowseBoardsPage {
        val jiraErrors = JiraErrors(driver)
        driver.wait(
            patience,
            ExpectedConditions.or(
                ExpectedConditions.and(
                    ExpectedConditions.textToBePresentInElementLocated(By.cssSelector("#ghx-header h2"), "Boards"),
                    ExpectedConditions.presenceOfElementLocated(By.cssSelector("#ghx-content-main table.aui"))
                ),
                jiraErrors.anyCommonError()
            )
        )
        jiraErrors.assertNoErrors()
        return this
    }
}
