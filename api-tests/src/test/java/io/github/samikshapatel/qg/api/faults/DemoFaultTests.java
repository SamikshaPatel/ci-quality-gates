package io.github.samikshapatel.qg.api.faults;

import io.github.samikshapatel.qg.support.FaultMarker;
import io.github.samikshapatel.qg.support.Tier;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Deliberate fault tests — test equipment for the gates, not product tests (ADR-0005).
 *
 * They are tagged demo-fault (excluded from every normal run by the root POM) and each
 * is enabled only for the demo scenario it proves, chosen with -Ddemo.scenario=...
 * "Flaky" faults fail on their first attempt and pass on retry, so the gate classifies
 * them as flaky every time.
 */
class DemoFaultTests {

    /** Scenario smoke-code: a product assertion fails in smoke, so the gate classifies it CODE. */
    @Nested
    @Tag(Tier.DEMO_FAULT)
    @Tag(Tier.SMOKE)
    class SmokeCodeFault {
        @Test
        @EnabledIfSystemProperty(named = "demo.scenario", matches = "smoke-code")
        void pingContractChanged() {
            int expected = 200;
            int actual = 201; // simulates a release that changed the health-check contract
            assertEquals(expected, actual, "Ping contract changed: unexpected status code");
        }
    }

    /** Scenario critical-flake: passes on retry, but the critical gate gives no retry credit. */
    @Nested
    @Tag(Tier.DEMO_FAULT)
    @Tag(Tier.CRITICAL)
    class CriticalFlakeFault {
        @Test
        @EnabledIfSystemProperty(named = "demo.scenario", matches = "critical-flake")
        void bookingTotalIntermittentlyWrong() {
            if (FaultMarker.firstAttempt("critical-booking-total")) {
                fail("Simulated intermittent failure: booking total was 0 on first read");
            }
        }
    }

    /**
     * Scenarios flake-breach and quarantine-cap: non-critical tests that pass on retry.
     * Three of them push the flake rate over 20% (flake-breach → quarantine → rerun).
     * All six exceed the quarantine cap of 5 (quarantine-cap → PR blocked).
     */
    @Nested
    @Tag(Tier.DEMO_FAULT)
    @Tag(Tier.REGRESSION)
    class NonCriticalFlakeFaults {
        @Test
        @EnabledIfSystemProperty(named = "demo.scenario", matches = "flake-breach|quarantine-cap")
        void searchResultsOrderFlaky() {
            flakyOnce("search-order");
        }

        @Test
        @EnabledIfSystemProperty(named = "demo.scenario", matches = "flake-breach|quarantine-cap")
        void currencyFormattingFlaky() {
            flakyOnce("currency-format");
        }

        @Test
        @EnabledIfSystemProperty(named = "demo.scenario", matches = "flake-breach|quarantine-cap")
        void additionalNeedsTrimFlaky() {
            flakyOnce("needs-trim");
        }

        @Test
        @EnabledIfSystemProperty(named = "demo.scenario", matches = "quarantine-cap")
        void paginationBoundaryFlaky() {
            flakyOnce("pagination");
        }

        @Test
        @EnabledIfSystemProperty(named = "demo.scenario", matches = "quarantine-cap")
        void dateLocaleFlaky() {
            flakyOnce("date-locale");
        }

        @Test
        @EnabledIfSystemProperty(named = "demo.scenario", matches = "quarantine-cap")
        void cacheHeaderFlaky() {
            flakyOnce("cache-header");
        }

        private void flakyOnce(String key) {
            if (FaultMarker.firstAttempt(key)) {
                fail("Simulated flaky failure (" + key + "): fails first attempt, passes on retry");
            }
        }
    }
}
