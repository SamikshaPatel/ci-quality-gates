package io.github.samikshapatel.qg.ui;

import io.github.samikshapatel.qg.support.Tier;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Non-critical regression: sorting, cart edits, validation, product detail. Eligible for quarantine. */
@Tag(Tier.REGRESSION)
class StoreRegressionTest extends BaseUiTest {

    @Test
    void sortByPriceLowToHigh() {
        loginAsStandardUser();
        byTest("product-sort-container").selectOption("lohi");
        List<Double> prices = new ArrayList<>();
        for (String p : byTest("inventory-item-price").allTextContents()) {
            prices.add(Double.parseDouble(p.replace("$", "")));
        }
        List<Double> sorted = new ArrayList<>(prices);
        sorted.sort(Comparator.naturalOrder());
        assertEquals(sorted, prices, "Prices should be ascending");
    }

    @Test
    void sortByNameZtoA() {
        loginAsStandardUser();
        byTest("product-sort-container").selectOption("za");
        List<String> names = byTest("inventory-item-name").allTextContents();
        List<String> sorted = new ArrayList<>(names);
        sorted.sort(Comparator.reverseOrder());
        assertEquals(sorted, names, "Names should be Z to A");
    }

    @Test
    void removeFromCartClearsBadge() {
        loginAsStandardUser();
        byTest("add-to-cart-sauce-labs-backpack").click();
        byTest("remove-sauce-labs-backpack").click();
        assertThat(byTest("shopping-cart-badge")).hasCount(0);
    }

    @Test
    void cartKeepsItemsAcrossPages() {
        loginAsStandardUser();
        byTest("add-to-cart-sauce-labs-bike-light").click();
        byTest("shopping-cart-link").click();
        byTest("continue-shopping").click();
        byTest("shopping-cart-link").click();
        assertThat(byTest("inventory-item-name")).hasText("Sauce Labs Bike Light");
    }

    @Test
    void checkoutRequiresPostalCode() {
        loginAsStandardUser();
        byTest("add-to-cart-sauce-labs-backpack").click();
        byTest("shopping-cart-link").click();
        byTest("checkout").click();
        byTest("firstName").fill("Ada");
        byTest("lastName").fill("Lovelace");
        byTest("continue").click();
        assertThat(byTest("error")).containsText("Postal Code is required");
    }

    @Test
    void productDetailShowsPrice() {
        loginAsStandardUser();
        byTest("inventory-item-name").first().click();
        assertThat(byTest("inventory-item-price")).containsText("$");
    }
}
