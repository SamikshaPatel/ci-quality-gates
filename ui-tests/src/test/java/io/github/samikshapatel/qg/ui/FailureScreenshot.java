package io.github.samikshapatel.qg.ui;

import com.microsoft.playwright.Page;
import io.qameta.allure.Allure;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.io.ByteArrayInputStream;

/**
 * When a UI test fails, attaches a full-page screenshot and the page URL to it in the Allure report.
 * Runs after the test body but before @AfterEach, so the page is still open.
 */
public class FailureScreenshot implements AfterTestExecutionCallback {

    @Override
    public void afterTestExecution(ExtensionContext context) {
        if (context.getExecutionException().isEmpty()) {
            return;
        }
        context.getTestInstance()
                .filter(BaseUiTest.class::isInstance)
                .map(BaseUiTest.class::cast)
                .filter(test -> test.page != null && !test.page.isClosed())
                .ifPresent(test -> {
                    try {
                        byte[] png = test.page.screenshot(new Page.ScreenshotOptions().setFullPage(true));
                        Allure.addAttachment("Screenshot at failure", "image/png", new ByteArrayInputStream(png), "png");
                        Allure.addAttachment("Page URL at failure", test.page.url());
                    } catch (RuntimeException e) {
                        Allure.addAttachment("Screenshot unavailable", e.getMessage());
                    }
                });
    }
}
