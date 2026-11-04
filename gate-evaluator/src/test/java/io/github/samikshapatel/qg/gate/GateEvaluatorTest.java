package io.github.samikshapatel.qg.gate;

import io.github.samikshapatel.qg.gate.TestResult.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every branch of the CI contract, checked against the real quality-gates.yml. */
class GateEvaluatorTest {

    private static final String PKG = "io.github.samikshapatel.qg.";
    private GateEvaluator evaluator;
    private QuarantineStore quarantine;
    private final List<String> tickets = new ArrayList<>();

    @TempDir
    Path tmp;

    @BeforeEach
    void setUp() {
        evaluator = new GateEvaluator(GateConfig.load(Path.of("../quality-gates.yml")));
        quarantine = QuarantineStore.load(tmp.resolve("quarantine.json"));
        tickets.clear();
    }

    private GateResult run(String tier, int attempt, List<TestResult> results) {
        return evaluator.evaluate(tier, results, attempt, quarantine, t -> {
            String key = "QG-" + (tickets.size() + 1);
            tickets.add(key);
            return key;
        }, "2026-11-04", "https://example/run/1");
    }

    private static TestResult t(String cls, String method, Status status, String type, String message) {
        return new TestResult(PKG + cls + "#" + method, PKG + cls, method, status, type, message);
    }

    private static TestResult pass(String cls, String method) {
        return t(cls, method, Status.PASSED, null, null);
    }

    private static List<TestResult> passing(String cls, int n) {
        List<TestResult> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(pass(cls, "test" + i));
        }
        return list;
    }

    @Test
    void smokePassesWhenEverythingPasses() {
        GateResult r = run("smoke", 1, passing("api.ApiSmokeTest", 5));
        assertEquals(GateResult.Verdict.PASS, r.verdict);
        assertEquals(100.0, r.passRate);
    }

    @Test
    void smokeTimeoutIsEnvAndOwnedByDevOps() {
        List<TestResult> results = passing("api.ApiSmokeTest", 4);
        results.add(t("api.ApiSmokeTest", "healthCheckReturns201", Status.FAILED,
                "java.net.ConnectException", "Connection refused"));
        GateResult r = run("smoke", 1, results);
        assertEquals(GateResult.Verdict.FAIL, r.verdict);
        assertEquals("ENV", r.classification);
        assertEquals("DevOps", r.owner);
        assertTrue(r.reason.contains("pass rate 80%, below 100% threshold"), r.reason);
    }

    @Test
    void smokeAssertionIsCodeAndOwnedByAuthor() {
        List<TestResult> results = passing("api.ApiSmokeTest", 2);
        results.add(t("api.ApiSmokeTest", "healthCheckReturns201", Status.FAILED,
                "org.opentest4j.AssertionFailedError", "expected: <200> but was: <201>"));
        GateResult r = run("smoke", 1, results);
        assertEquals("CODE", r.classification);
        assertEquals("PR author", r.owner);
    }

    @Test
    void smoke504IsEnv() {
        List<TestResult> results = List.of(t("api.ApiSmokeTest", "bookingListReturnsIds", Status.FAILED,
                "java.lang.AssertionError", "1 expectation failed.\nExpected status code <200> but was <504>."));
        assertEquals("ENV", run("smoke", 1, results).classification);
    }

    @Test
    void smokeWithBothKindsIsMixed() {
        List<TestResult> results = List.of(
                t("api.ApiSmokeTest", "a", Status.FAILED, "java.net.ConnectException", "Connection refused"),
                t("ui.UiSmokeTest", "b", Status.FAILED, "AssertionFailedError", "Locator expected to have text"));
        GateResult r = run("smoke", 1, results);
        assertEquals("MIXED", r.classification);
        assertTrue(r.owner.contains("DevOps") && r.owner.contains("PR author"));
    }

    @Test
    void criticalGivesNoRetryCredit() {
        List<TestResult> results = passing("api.BookingCriticalTest", 8);
        results.add(t("api.BookingCriticalTest", "createBookingReturnsId", Status.FLAKY, "AssertionError", "blip"));
        GateResult r = run("critical", 1, results);
        assertEquals(GateResult.Verdict.FAIL, r.verdict);
        assertEquals(88.9, r.passRate);
        assertTrue(r.reason.contains("no retry credit"), r.reason);
        assertTrue(r.newlyQuarantined.isEmpty(), "critical tests are never quarantined");
    }

    @Test
    void regressionPassesWithFlakeRateWithinLimit() {
        List<TestResult> results = passing("api.BookingRegressionTest", 9);
        results.add(t("api.BookingRegressionTest", "x", Status.FLAKY, "AssertionError", "blip"));
        GateResult r = run("regression", 1, results);
        assertEquals(GateResult.Verdict.PASS, r.verdict);
        assertEquals(10.0, r.flakeRate);
    }

    @Test
    void regressionFlakeBreachQuarantinesAndTickets() {
        List<TestResult> results = passing("api.BookingRegressionTest", 11);
        for (int i = 0; i < 3; i++) {
            results.add(t("api.faults.DemoFaultTests$NonCriticalFlakeFaults", "flaky" + i, Status.FLAKY,
                    "AssertionFailedError", "Simulated flaky failure"));
        }
        GateResult r = run("regression", 1, results);
        assertEquals(GateResult.Verdict.QUARANTINE_RERUN, r.verdict);
        assertEquals(21.4, r.flakeRate);
        assertEquals(3, quarantine.size());
        assertEquals(List.of("QG-1", "QG-2", "QG-3"), tickets);
        assertTrue(r.reason.contains("flake rate 21.4%, above 20% limit"), r.reason);
    }

    @Test
    void quarantineCapBlocksThePr() {
        List<TestResult> results = passing("api.BookingRegressionTest", 11);
        for (int i = 0; i < 6; i++) {
            results.add(t("api.faults.DemoFaultTests$NonCriticalFlakeFaults", "flaky" + i, Status.FLAKY,
                    "AssertionFailedError", "Simulated flaky failure"));
        }
        GateResult r = run("regression", 1, results);
        assertEquals(GateResult.Verdict.FAIL, r.verdict);
        assertEquals("QA lead", r.owner);
        assertTrue(r.reason.contains("quarantine cap reached: 6 tests would be quarantined, cap is 5"), r.reason);
        assertEquals(0, quarantine.size(), "nothing is quarantined when the cap refuses");
        assertTrue(tickets.isEmpty());
    }

    @Test
    void protectedTestsAreNeverQuarantined() {
        List<TestResult> results = passing("ui.StoreRegressionTest", 3);
        results.add(t("ui.CheckoutEdgeCasesTest", "promoCode", Status.FLAKY, "AssertionError", "blip"));
        GateResult r = run("regression", 1, results);
        assertEquals(GateResult.Verdict.FAIL, r.verdict);
        assertTrue(r.reason.contains("never-quarantine"), r.reason);
        assertEquals(0, quarantine.size());
    }

    @Test
    void hardFailuresBelowThresholdAreNotQuarantined() {
        List<TestResult> results = passing("api.BookingRegressionTest", 3);
        results.add(t("api.BookingRegressionTest", "a", Status.FAILED, "AssertionError", "wrong"));
        results.add(t("api.BookingRegressionTest", "b", Status.FAILED, "AssertionError", "wrong"));
        GateResult r = run("regression", 1, results);
        assertEquals(GateResult.Verdict.FAIL, r.verdict);
        assertEquals(60.0, r.passRate);
        assertTrue(r.reason.contains("pass rate 60%, below 80% threshold"), r.reason);
        assertEquals(0, quarantine.size());
    }

    @Test
    void flakinessThatPersistsAfterRerunFails() {
        List<TestResult> results = passing("api.BookingRegressionTest", 3);
        results.add(t("api.BookingRegressionTest", "a", Status.FLAKY, "AssertionError", "blip"));
        GateResult r = run("regression", 2, results);
        assertEquals(GateResult.Verdict.FAIL, r.verdict);
        assertTrue(r.reason.endsWith("after quarantine and rerun"), r.reason);
    }

    @Test
    void noTestsExecutedFailsTheGate() {
        GateResult r = run("smoke", 1, List.of());
        assertEquals(GateResult.Verdict.FAIL, r.verdict);
        assertTrue(r.reason.contains("no tests executed"));
    }
}
