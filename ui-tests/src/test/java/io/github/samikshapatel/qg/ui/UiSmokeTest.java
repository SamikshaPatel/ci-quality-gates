package io.github.samikshapatel.qg.ui;

import io.github.samikshapatel.qg.support.Config;
import io.github.samikshapatel.qg.support.Tier;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/** UI smoke: does the site load, and can a user get in? Hard gate, no retries. */
@Tag(Tier.SMOKE)
class UiSmokeTest extends BaseUiTest {

    @Test
    void loginPageLoads() {
        page.navigate(Config.uiBaseUrl());
        assertThat(byTest("login-button")).isVisible();
    }

    @Test
    void standardUserReachesInventory() {
        loginAsStandardUser();
        assertThat(byTest("title")).hasText("Products");
    }
}
