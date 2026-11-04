package io.github.samikshapatel.qg.ui;

import io.github.samikshapatel.qg.support.Tier;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Critical path: getting in, buying, getting out. Gated at 100%, never quarantined.
 * The class name also matches the "*Checkout*" never-quarantine rule in quality-gates.yml.
 */
@Tag(Tier.CRITICAL)
class CheckoutCriticalTest extends BaseUiTest {

    @Test
    void addToCartUpdatesBadge() {
        loginAsStandardUser();
        byTest("add-to-cart-sauce-labs-backpack").click();
        assertThat(byTest("shopping-cart-badge")).hasText("1");
    }

    @Test
    void completeCheckoutShowsConfirmation() {
        loginAsStandardUser();
        byTest("add-to-cart-sauce-labs-backpack").click();
        byTest("shopping-cart-link").click();
        byTest("checkout").click();
        byTest("firstName").fill("Ada");
        byTest("lastName").fill("Lovelace");
        byTest("postalCode").fill("L6Y 1A1");
        byTest("continue").click();
        byTest("finish").click();
        assertThat(byTest("complete-header")).hasText("Thank you for your order!");
    }

    @Test
    void lockedOutUserSeesError() {
        login("locked_out_user");
        assertThat(byTest("error")).containsText("locked out");
    }

    @Test
    void logoutReturnsToLogin() {
        loginAsStandardUser();
        page.locator("#react-burger-menu-btn").click();
        byTest("logout-sidebar-link").click();
        assertThat(byTest("login-button")).isVisible();
    }
}
