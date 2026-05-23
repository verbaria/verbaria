/*
 * Copyright 2010, Red Hat, Inc. and individual contributors as indicated by the
 * @author tags. See the copyright.txt file in the distribution for a full
 * listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 */
package org.zanata.page;

import static org.assertj.core.api.Assertions.assertThat;
import static org.zanata.page.utility.PageSource.shortenPageSource;
import static org.zanata.util.FluentWaitExt.until;

import java.util.List;
import java.util.Set;

import org.openqa.selenium.Alert;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.NoAlertPresentException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.pagefactory.AjaxElementLocatorFactory;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.FluentWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zanata.util.WebElementUtil;

/**
 * The base class for the page driver. Contains functionality not generally of
 * user-visible nature.
 *
 * Java port of the former AbstractPage.kt.
 */
public abstract class AbstractPage {
    private static final Logger log = LoggerFactory.getLogger(AbstractPage.class);

    private final WebDriver driver;

    public AbstractPage(WebDriver driver) {
        this.driver = driver;
        PageFactory.initElements(new AjaxElementLocatorFactory(driver, 10), this);
        waitForPageSilence();
    }

    public WebDriver getDriver() {
        return driver;
    }

    public void reload() {
        log.info("Sys: Reload");
        driver.navigate().refresh();
    }

    public void deleteCookiesAndRefresh() {
        log.info("Sys: Delete cookies, reload");
        driver.manage().deleteAllCookies();
        if (!driver.manage().getCookies().isEmpty()) {
            log.warn("Failed to delete cookies: {}", driver.manage().getCookies());
        }
        driver.navigate().refresh();
    }

    protected JavascriptExecutor getExecutor() {
        return (JavascriptExecutor) driver;
    }

    @SuppressWarnings("unchecked")
    private List<WebElement> executeScriptToElements(String script) {
        return (List<WebElement>) getExecutor().executeScript(script);
    }

    public String getUrl() {
        return driver.getCurrentUrl();
    }

    protected void logWaiting(String msg) {
        log.info("Waiting for {}", msg);
    }

    protected void logFinished(String msg) {
        log.debug("Finished {}", msg);
    }

    public FluentWait<WebDriver> waitForAMoment() {
        return WebElementUtil.waitForAMoment(driver);
    }

    /**
     * @deprecated chromedriver has issues with alert popups.
     */
    @Deprecated
    public Alert switchToAlert() {
        return until(waitForAMoment(), "alert", d -> {
            try {
                return d.switchTo().alert();
            } catch (NoAlertPresentException e) {
                return null;
            }
        });
    }

    protected <P extends AbstractPage, T> T refreshPageUntil(P currentPage,
            String message, java.util.function.Function<WebDriver, T> function) {
        T done = waitForAMoment().withMessage(message).until(function::apply);
        PageFactory.initElements(driver, currentPage);
        return done;
    }

    /**
     * Most pages have no outstanding ajax requests once idle; some (e.g. the
     * editor's event service) use long polling so override this to declare
     * how many requests are expected to remain active.
     */
    protected int getExpectedBackgroundRequests() {
        return 0;
    }

    public void execAndWaitForNewPage(Runnable runnable) {
        WebElement oldPage = driver.findElement(By.tagName("html"));
        runnable.run();
        String msg = "new page load";
        logWaiting(msg);
        until(waitForAMoment(), msg, d -> {
            try {
                oldPage.getAttribute("class");
                return false;
            } catch (StaleElementReferenceException e) {
                String script = "return document.readyState === 'complete' && window.deferScriptsFinished";
                Object documentComplete = getExecutor().executeScript(script);
                return Boolean.TRUE.equals(documentComplete);
            }
        });
        logFinished(msg);
    }

    /**
     * Wait for any AJAX/timeout requests to return.
     */
    public void waitForPageSilence() {
        String script = "return XMLHttpRequest.active";
        waitForAMoment().withMessage("page silence").until(d -> {
            Object raw = getExecutor().executeScript(script);
            if (raw == null) {
                if (log.isWarnEnabled()) {
                    String url = driver.getCurrentUrl();
                    String pageSource = shortenPageSource(driver.getPageSource());
                    log.warn(
                            "XMLHttpRequest.active is null. Is zanata-testing-extension installed? URL: {}\nPartial page source follows:\n{}",
                            url, pageSource);
                }
                return true;
            }
            long outstanding = ((Number) raw).longValue();
            if (outstanding < 0) {
                throw new RuntimeException(
                        "XMLHttpRequest.active and/or window.timeoutCounter is negative. Please check zanata-testing-extension.");
            }
            int expected = getExpectedBackgroundRequests();
            if (outstanding < expected) {
                log.warn("Expected at least {} background requests, but actual count is {}",
                        expected, outstanding, new Throwable());
            } else {
                log.debug("Waiting: outstanding = {}, expected = {}", outstanding, expected);
            }
            return outstanding <= expected;
        });
        waitForLoaders();
    }

    private void waitForLoaders() {
        waitForAMoment().withMessage("Loader indicator").until(d -> {
            String script = "return (typeof $ == 'undefined') ?  [] : $('.js-loader').toArray()";
            List<WebElement> loaders = executeScriptToElements(script);
            for (WebElement loader : loaders) {
                if (loader.getAttribute("class").contains("is-active")) {
                    log.info("Wait for loader finished");
                    return false;
                }
            }
            return true;
        });
    }

    /** Expect an element to be interactive, and return it. */
    public WebElement readyElement(By elementBy) {
        String msg = "element ready: " + elementBy;
        logWaiting(msg);
        waitForPageSilence();
        WebElement targetElement = existingElement(elementBy);
        waitForElementReady(targetElement);
        assertReady(targetElement);
        return targetElement;
    }

    public WebElement readyElement(WebElement parentElement, By elementBy) {
        String msg = "child ready: " + elementBy;
        logWaiting(msg);
        waitForPageSilence();
        WebElement targetElement = existingElement(parentElement, elementBy);
        assertReady(targetElement);
        return targetElement;
    }

    public WebElement existingElement(By elementBy) {
        String msg = "element exists: " + elementBy;
        logWaiting(msg);
        waitForPageSilence();
        return until(waitForAMoment(), msg, d -> d.findElement(elementBy));
    }

    public WebElement existingElement(WebElement parentElement, By elementBy) {
        String msg = "child exists: " + elementBy;
        logWaiting(msg);
        waitForPageSilence();
        return until(waitForAMoment(), msg, d -> parentElement.findElement(elementBy));
    }

    public void clickElement(By findby) {
        clickElement(readyElement(findby));
    }

    public void clickElement(WebElement element) {
        removeNotifications();
        waitForNotificationsGone();
        dismissCookieConsent();
        scrollIntoView(element);
        waitForAMoment().withMessage("element clickable: " + element)
                .until(ExpectedConditions.elementToBeClickable(element));
        element.click();
    }

    public void enterText(WebElement element, String text) {
        enterText(element, text, true, false, true);
    }

    public void enterText(WebElement element, String text, boolean clear) {
        enterText(element, text, clear, false, true);
    }

    public void enterText(WebElement element, String text,
            boolean clear, boolean inject, boolean check) {
        removeNotifications();
        waitForNotificationsGone();
        dismissCookieConsent();
        scrollIntoView(element);
        triggerScreenshot("_pretext");
        waitForAMoment().withMessage("element editable: " + element)
                .until(ExpectedConditions.elementToBeClickable(element));
        if (inject) {
            if (clear) {
                element.clear();
            }
            element.sendKeys(text);
        } else {
            Actions enterTextAction = new Actions(driver).moveToElement(element);
            enterTextAction = enterTextAction.click();
            waitForPageSilence();
            if (clear) {
                enterTextAction = enterTextAction.sendKeys(Keys.chord(Keys.CONTROL, "a"))
                        .sendKeys(Keys.DELETE);
                waitForPageSilence();
            }
            enterTextAction.sendKeys(text).perform();
        }
        if (check) {
            until(waitForAMoment(), "Text equal to entered", d -> {
                String foundText = element.getAttribute("value");
                if (!text.equals(foundText)) {
                    log.info("Found: {}", foundText);
                    triggerScreenshot("_textWaiting");
                    return false;
                }
                return true;
            });
        } else {
            log.info("Not checking text entered");
        }
        triggerScreenshot("_text");
    }

    public void enterText(By findBy, String text) {
        enterText(readyElement(findBy), text);
    }

    public String getText(By findBy) {
        return getText(existingElement(findBy));
    }

    public String getText(WebElement webElement) {
        scrollIntoView(webElement);
        waitForElementReady(webElement);
        return webElement.getText();
    }

    public String getAttribute(By findBy, String attribute) {
        return getAttribute(existingElement(findBy), attribute);
    }

    public String getAttribute(WebElement webElement, String attribute) {
        String value = webElement.getAttribute(attribute);
        return value == null ? "" : value;
    }

    public void touchTextField(WebElement textField) {
        waitForAMoment().until(d -> {
            enterText(textField, ".", true, false, false);
            return ".".equals(textField.getAttribute("value"));
        });
        textField.clear();
    }

    private void waitForElementReady(WebElement elem) {
        waitForAMoment().withMessage("element ready: " + elem)
                .until(d -> elem.isDisplayed() && elem.isEnabled());
    }

    private void assertReady(WebElement targetElement) {
        assertThat(targetElement.isDisplayed()).as("displayed").isTrue();
        assertThat(targetElement.isEnabled()).as("enabled").isTrue();
    }

    public void removeNotifications() {
        List<WebElement> notifications = executeScriptToElements(
                "return (typeof $ == 'undefined') ?  [] : $('a.message__remove').toArray()");
        log.info("Closing {} notifications", notifications.size());
        for (WebElement notification : notifications) {
            try {
                notification.click();
            } catch (WebDriverException exc) {
                log.info("Missed a notification X click");
            }
        }
        String script = "return (typeof $ == 'undefined') ?  [] : $('ul.message--global').toArray()";
        List<WebElement> messageBoxes = executeScriptToElements(script);
        for (WebElement messageBox : messageBoxes) {
            getExecutor().executeScript(
                    "arguments[0].setAttribute('class', arguments[1]);",
                    messageBox,
                    messageBox.getAttribute("class").replace("is-active", ""));
        }
    }

    public void waitForNotificationsGone() {
        String script = "return (typeof $ == 'undefined') ?  [] : $('ul.message--global').toArray()";
        String message = "notifications box not displayed";
        waitForAMoment().withMessage(message).until(d -> {
            List<WebElement> boxes = executeScriptToElements(script);
            for (WebElement box : boxes) {
                if (box.isDisplayed()) {
                    log.info(message);
                    return false;
                }
            }
            return true;
        });
    }

    public void defocus() {
        log.info("Click off element focus");
        java.util.List<WebElement> webElements = driver.findElements(By.id("container"));
        webElements.addAll(driver.findElements(By.tagName("body")));
        if (!webElements.isEmpty()) {
            webElements.get(0).click();
        } else {
            log.warn("Unable to focus page container");
        }
        waitForPageSilence();
    }

    public void defocus(By elementBy) {
        log.info("Force unfocus");
        WebElement element = existingElement(elementBy);
        getExecutor().executeScript("arguments[0].blur()", element);
        waitForPageSilence();
    }

    public void dismissCookieConsent() {
        By consentButton = By.className("cc-dismiss");
        if (!driver.findElements(consentButton).isEmpty()
                && driver.findElement(consentButton).isDisplayed()) {
            log.info("Closing Cookie Consent popup");
            existingElement(By.className("cc-dismiss")).click();
        }
        waitForPageSilence();
    }

    public void slightPause() {
        waitForPageSilence();
    }

    public void scrollIntoView(WebElement targetElement) {
        getExecutor().executeScript("arguments[0].scrollIntoView(true);", targetElement);
    }

    public void scrollToTop() {
        getExecutor().executeScript("scroll(0, 0);");
    }

    public String getHtmlSource(WebElement webElement) {
        return (String) getExecutor().executeScript("return arguments[0].innerHTML;", webElement);
    }

    public void triggerScreenshot(String tag) {
        WebElementUtil.triggerScreenshot(tag);
    }

    public Set<String> getAllWindowHandles() {
        return driver.getWindowHandles();
    }
}
