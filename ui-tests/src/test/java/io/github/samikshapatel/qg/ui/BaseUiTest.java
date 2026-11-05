package io.github.samikshapatel.qg.ui;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Tracing;
import io.github.samikshapatel.qg.support.Config;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.file.Path;

/**
 * Shared Playwright setup for SauceDemo. One browser per class, a fresh context per test,
 * and a trace saved for every test so a failure can be replayed without rerunning it.
 */
@ExtendWith(FailureScreenshot.class)
abstract class BaseUiTest {

    private static Playwright playwright;
    private static Browser browser;
    protected BrowserContext context;
    protected Page page;

    @BeforeAll
    static void launchBrowser() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
    }

    @AfterAll
    static void closeBrowser() {
        if (playwright != null) {
            playwright.close();
        }
    }

    @BeforeEach
    void openPage() {
        context = browser.newContext();
        context.setDefaultTimeout(15_000);
        context.tracing().start(new Tracing.StartOptions().setScreenshots(true).setSnapshots(true));
        page = context.newPage();
    }

    @AfterEach
    void closePage(TestInfo info) {
        String name = info.getTestClass().map(Class::getSimpleName).orElse("ui") + "-"
                + info.getTestMethod().map(m -> m.getName()).orElse("test");
        context.tracing().stop(new Tracing.StopOptions().setPath(Path.of("target", "traces", name + ".zip")));
        context.close();
    }

    protected Locator byTest(String dataTest) {
        return page.locator("[data-test=\"" + dataTest + "\"]");
    }

    protected void login(String username) {
        page.navigate(Config.uiBaseUrl());
        byTest("username").fill(username);
        byTest("password").fill(Config.uiPassword());
        byTest("login-button").click();
    }

    protected void loginAsStandardUser() {
        login(Config.uiUsername());
        byTest("inventory-container").waitFor();
    }
}
