package io.github.samikshapatel.qg.gate;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunReportTest {

    private final Map<String, String> env = Map.of(
            "GITHUB_EVENT_NAME", "pull_request", "PR_NUMBER", "14",
            "GITHUB_SHA", "a3f9c21e0000000000000000000000000000beef",
            "GITHUB_SERVER_URL", "https://github.com", "GITHUB_REPOSITORY", "SamikshaPatel/ci-quality-gates",
            "GITHUB_RUN_ID", "42");

    private static GateResult smokeEnvFailure() {
        GateResult r = new GateResult();
        r.tier = "smoke";
        r.verdict = GateResult.Verdict.FAIL;
        r.executed = 5;
        r.passed = 4;
        r.failed = 1;
        r.effectivePassed = 4;
        r.passRate = 80;
        r.passRateThreshold = 100;
        r.classification = "ENV";
        r.reason = "Smoke gate breached: pass rate 80%, below 100% threshold (1 failed, classified ENV)";
        r.action = "Hard stop: build marked failed, PR blocked.";
        r.owner = "DevOps";
        r.runbook = "docs/runbooks/smoke-failure.md";
        r.nextStep = "Check the service containers.";
        r.failures.add(new GateResult.Failure("io.github.samikshapatel.qg.api.ApiSmokeTest#healthCheckReturns201",
                "FAILED", "ENV", "java.net.ConnectException Connection refused"));
        return r;
    }

    @Test
    void smokeFailureReportTellsWhoActsNext() {
        Map<String, GateResult> results = new LinkedHashMap<>();
        results.put("smoke", smokeEnvFailure());
        String md = new RunReport(env).render(results, Optional.empty());

        assertTrue(md.startsWith(RunReport.MARKER));
        assertTrue(md.contains("PR #14 · commit `a3f9c21`"));
        assertTrue(md.contains("**Result: ❌ FAILED — merge blocked**"));
        assertTrue(md.contains("| Smoke | ❌ FAIL | 4/5 passed (80%) | ≥ 100% pass, no retries |"));
        assertTrue(md.contains("| Critical tier | ⏭️ SKIPPED | skipped (earlier gate failed) | — |"));
        assertTrue(md.contains("**Owner:** DevOps"));
        assertTrue(md.contains("`ApiSmokeTest.healthCheckReturns201`"));
        assertTrue(md.contains("https://github.com/SamikshaPatel/ci-quality-gates/actions/runs/42"));
    }

    @Test
    void allGatesPassingAllowsMerge() {
        Map<String, GateResult> results = new LinkedHashMap<>();
        for (String tier : RunReport.ORDER) {
            GateResult r = new GateResult();
            r.tier = tier;
            r.verdict = GateResult.Verdict.PASS;
            r.executed = 4;
            r.effectivePassed = 4;
            r.passRate = 100;
            r.passRateThreshold = "regression".equals(tier) ? 80 : 100;
            results.put(tier, r);
        }
        String md = new RunReport(env).render(results, Optional.empty());
        assertTrue(md.contains("**Result: ✅ PASSED — merge allowed**"));
        assertFalse(md.contains("What failed"));
    }

    @Test
    void failureFileNamesGateValueAndThreshold() {
        String text = RunReport.failureFile(smokeEnvFailure());
        assertTrue(text.startsWith("QUALITY GATE BREACHED\ngate: smoke\n"));
        assertTrue(text.contains("pass_rate: 80.0 (threshold 100.0)"));
        assertTrue(text.contains("owner: DevOps"));
    }

    @Test
    void nightlyReviewReleasesAfterStreakAndFlagsOverdue() {
        GateConfig config = GateConfig.load(java.nio.file.Path.of("../quality-gates.yml"));
        QuarantineStore store = QuarantineStore.load(java.nio.file.Path.of("target/does-not-exist.json"));
        QuarantineStore.Entry almost = new QuarantineStore.Entry("a.B#ready", "2026-11-01", "flaky", null, "run");
        almost.greenStreak = 6;
        QuarantineStore.Entry old = new QuarantineStore.Entry("a.B#old", "2026-10-01", "flaky", null, "run");
        store.add(almost);
        store.add(old);
        List<TestResult> results = List.of(
                new TestResult("a.B#ready", "a.B", "ready", TestResult.Status.PASSED, null, null),
                new TestResult("a.B#old", "a.B", "old", TestResult.Status.FLAKY, "x", "y"));
        QuarantineReview.Summary s = QuarantineReview.review(store, results, config, LocalDate.parse("2026-11-04"));
        assertEquals(List.of("a.B#ready"), s.released);
        assertEquals(List.of("a.B#old"), s.overdue);
        assertEquals(1, s.remaining);
    }
}
