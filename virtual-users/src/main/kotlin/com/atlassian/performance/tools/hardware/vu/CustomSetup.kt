package com.atlassian.performance.tools.hardware.vu

import com.atlassian.performance.tools.jiraactions.api.SET_UP
import com.atlassian.performance.tools.jiraactions.api.WebJira
import com.atlassian.performance.tools.jiraactions.api.action.Action
import com.atlassian.performance.tools.jiraactions.api.measure.ActionMeter
import com.atlassian.performance.tools.jiraactions.api.page.AdminAccess
import com.atlassian.performance.tools.jiraactions.api.page.wait
import org.apache.logging.log4j.LogManager
import org.openqa.selenium.By
import org.openqa.selenium.WebDriver
import org.openqa.selenium.support.ui.ExpectedConditions.*
import java.time.Duration
import java.time.Duration.ofSeconds
import java.time.Instant


class CustomSetup(
    jira: WebJira,
    private val meter: ActionMeter
) : Action {
    private val logger = LogManager.getLogger(this::class.java)

    private val driver: WebDriver = jira.driver
    private val access: AdminAccess = AdminAccess(driver, jira, "admin")
    private val switchLocator = By.id("rte-switch")

    override fun run() {
        meter.measure(SET_UP) {
            disable()
        }
    }

    /**
     * Makes sure RTE is disabled, even if the feature doesn't exist at all.
     * Supports both enabled and disabled websudo.
     */
    private fun disable() {
        val prompted = access.isPrompted()
        if (prompted) {
            access.gain()
        }
        if (isSwitchPresent()) {
            ensureSwitchIsOff()
        } else {
            logger.info("This Jira does not support RTE configuration, so RTE should be de facto disabled")
        }
        if (prompted) {
            access.drop()
        }
    }

    private fun isSwitchPresent(): Boolean {
        return CustomPatience().test { driver.findElements(switchLocator).isNotEmpty() }
    }

    /**
     * The switch always requires admin access, but it might not be granted yet.
     * For example Jira 7.2.0 did not enforce admin access on RTE config page.
     */
    private fun ensureSwitchIsOff() {
        if (getSwitchInput().isSelected) {
            logger.info("RTE is enabled, disabling...")
            if (access.isGranted()) {
                logger.info("Admin access already granted, clicking the RTE switch...")
                driver.wait(ofSeconds(5), elementToBeClickable(switchLocator)).click()
                driver.wait(ofSeconds(15), not(attributeToBe(getSwitchInput(), "aria-busy", "true")))
                driver.wait(ofSeconds(5), not(elementToBeSelected(getSwitchInput())))
                logger.info("RTE should be disabled now")
            } else {
                logger.info("Admin access not granted yet, gaining access proactively and retrying...")
                access.gainProactively()
                ensureSwitchIsOff()
            }
        } else {
            logger.info("RTE is already disabled")
        }
    }

    private fun getSwitchInput() = driver.wait(ofSeconds(5), elementToBeClickable(By.id("rte-switch-input")))
}

private class CustomPatience(
    private val timeout: Duration = ofSeconds(10)
) {
    fun test(
        condition: () -> Boolean
    ): Boolean {
        val start = Instant.now()
        val deadline = start.plus(timeout)
        while (true) {
            if (condition.invoke()) {
                return true
            }
            if (Instant.now().isAfter(deadline)) {
                return false
            }
            Thread.sleep(50)
        }
    }
}