package org.zanata.page.utility;

import org.openqa.selenium.WebDriver;
import org.zanata.util.ShortString;

public final class PageSource {
    private PageSource() {}

    public static String shortenPageSource(WebDriver driver) {
        return shortenPageSource(driver.getPageSource());
    }

    /**
     * Summarises the HTML for a page. Chrome's dinosaur game injects huge
     * base64 PNG payloads when offline; collapse those to a stub.
     */
    public static String shortenPageSource(String pageSource) {
        if (pageSource.contains("data:image/png;base64")
                && pageSource.contains("ERR_INTERNET_DISCONNECTED")) {
            return "ERR_INTERNET_DISCONNECTED";
        }
        return ShortString.shorten(pageSource, 2000);
    }
}
