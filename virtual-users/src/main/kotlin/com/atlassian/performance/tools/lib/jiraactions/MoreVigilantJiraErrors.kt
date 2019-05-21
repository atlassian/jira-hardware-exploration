package com.atlassian.performance.tools.lib.jiraactions

import org.openqa.selenium.By
import org.openqa.selenium.WebDriver
import org.openqa.selenium.WebElement
import org.openqa.selenium.support.ui.ExpectedCondition
import org.openqa.selenium.support.ui.ExpectedConditions.or
import org.openqa.selenium.support.ui.ExpectedConditions.presenceOfElementLocated

class MoreVigilantJiraErrors(
    private val driver: WebDriver
) {
    private val errorLocators = listOf(
        By.cssSelector("div.aui-message.error"),
        By.id("errorPageContainer"),
        By.cssSelector("div.form-body div.error")
    )
    private val warningMessageLocator = By.cssSelector("section div.aui-message.warning")

    fun anyCommonError(): ExpectedCondition<Boolean> {
        val conditions = errorLocators.map { presenceOfElementLocated(it) }.toTypedArray()
        return or(*conditions)
    }

    fun anyCommonWarning(): ExpectedCondition<WebElement> {
        return presenceOfElementLocated(warningMessageLocator)
    }

    fun assertNoErrors() {
        errorLocators.forEach { locator ->
            driver
                .findElements(locator)
                .firstOrNull()
                ?.let { throw Exception("Error at $locator reads: ${it.text}") }
        }
    }
}
