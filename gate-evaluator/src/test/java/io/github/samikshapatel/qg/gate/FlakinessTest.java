package io.github.samikshapatel.qg.gate;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlakinessTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String REG = "io.x.api.BookingRegressionTest#";
    private static final Flakiness.Settings SETTINGS = new Flakiness.Settings(30, 5, 10.0);

    @TempDir
    Path tmp;

    private int runCounter;

    /** Writes one run folder: its history entry and its test outcomes. */
    private void run(String branch, String pr, String scenario, String verdict, String classification,
                     String... outcomes) throws IOException {
        runCounter++;
        Path dir = Files.createDirectories(tmp.resolve("runs/run" + runCounter));
        RunHistory.Entry e = new RunHistory.Entry();
        e.folder = "run" + runCounter;
        e.date = String.format("2026-11-%02dT06:00:00Z", runCounter);
        e.branch = branch;
        e.pr = pr;
        e.scenario = scenario;
        e.verdict = verdict;
        e.classification = classification;
        MAPPER.writeValue(dir.resolve("history-entry.json").toFile(), e);
        List<Flakiness.Outcome> list = new ArrayList<>();
        for (String o : outcomes) {
            String[] parts = o.split("=");
            list.add(new Flakiness.Outcome(REG + parts[0], "regression", parts[1]));
        }
        MAPPER.writeValue(dir.resolve("tests.json").toFile(), list);
    }

    private void mainRun(String... outcomes) throws IOException {
        run("main", null, "none", "PASSED", null, outcomes);
    }

    @Test
    void scoresCountFailuresAndRetriesOnMainOnly() throws IOException {
        for (int i = 0; i < 8; i++) {
            mainRun("steady=PASSED", "shaky=" + (i % 4 == 0 ? "FLAKY" : "PASSED"));
        }
        mainRun("steady=PASSED", "shaky=FAILED");
        mainRun("steady=PASSED", "shaky=PASSED");
        run("feature/x", "12", "none", "FAILED", "CODE", "steady=FAILED", "shaky=FAILED");
        run("main", null, "flake-breach", "PASSED", null, "steady=FLAKY");
        run("main", null, "none", "FAILED", "ENV", "steady=FAILED", "shaky=FAILED");

        Flakiness.Report r = Flakiness.compute(tmp.resolve("runs"), SETTINGS, "now");
        assertEquals(10, r.eligibleRuns, "PR, demo and environment-down runs are excluded");
        Flakiness.Score shaky = r.find(REG + "shaky");
        assertEquals(10, shaky.runs);
        assertEquals(2, shaky.flaky);
        assertEquals(1, shaky.failed);
        assertEquals(30.0, shaky.score);
        assertEquals(0.0, r.find(REG + "steady").score);
        assertEquals(REG + "shaky", r.tests.get(0).id, "highest score first");
    }

    @Test
    void windowKeepsOnlyTheNewestRuns() throws IOException {
        for (int i = 0; i < 6; i++) {
            mainRun("t=FAILED");
        }
        for (int i = 0; i < 5; i++) {
            mainRun("t=PASSED");
        }
        Flakiness.Report r = Flakiness.compute(tmp.resolve("runs"), new Flakiness.Settings(5, 5, 10.0), "now");
        assertEquals(0.0, r.find(REG + "t").score);
    }

    @Test
    void verdictSeparatesNewFailuresFromKnownFlakiness() {
        Flakiness.Score clean = new Flakiness.Score();
        clean.runs = 20;
        assertTrue(Flakiness.verdict(clean, 5).contains("likely caused by this change"));
        Flakiness.Score known = new Flakiness.Score();
        known.runs = 20;
        known.flaky = 3;
        known.score = 15;
        assertTrue(Flakiness.verdict(known, 5).contains("3 of 20 main runs (15%): known flaky"));
        assertTrue(Flakiness.verdict(null, 5).contains("not enough"));
    }

    @Test
    void nightlyQuarantinesByScoreWithinTheCap() throws IOException {
        Flakiness.Report r = new Flakiness.Report();
        r.minRuns = 5;
        r.quarantineScore = 10;
        r.tests.add(score("io.x.api.BookingRegressionTest#flaky", "regression", 20, 25));
        r.tests.add(score("io.x.api.BookingCriticalTest#create", "critical", 20, 50));
        r.tests.add(score("io.x.ui.CheckoutEdgeTest#promo", "regression", 20, 40));
        r.tests.add(score("io.x.api.BookingRegressionTest#young", "regression", 3, 66.7));
        r.tests.add(score("io.x.api.BookingRegressionTest#fine", "regression", 20, 5));
        GateConfig config = GateConfig.load(Path.of("../quality-gates.yml"));
        QuarantineStore store = QuarantineStore.load(tmp.resolve("quarantine.json"));

        List<String> tickets = new ArrayList<>();
        Flakiness.ScoreQuarantine q = Flakiness.quarantineByScore(r, store, config,
                t -> { tickets.add(t.id()); return "QG-9"; }, "2026-11-05", "run");
        assertEquals(List.of("io.x.api.BookingRegressionTest#flaky"), q.added.stream().map(e -> e.id).toList(),
                "critical tier, protected names, too few runs and low scores are skipped");
        assertEquals("QG-9", q.added.get(0).jira);
        assertTrue(q.added.get(0).reason.startsWith("Flakiness score 25%"));

        for (int i = 0; i < 4; i++) {
            store.add(new QuarantineStore.Entry("x#" + i, "2026-11-05", "r", null, "run"));
        }
        r.tests.add(score("io.x.api.BookingRegressionTest#late", "regression", 20, 30));
        Flakiness.ScoreQuarantine full = Flakiness.quarantineByScore(r, store, config, t -> null, "2026-11-05", "run");
        assertTrue(full.added.isEmpty());
        assertEquals(List.of("io.x.api.BookingRegressionTest#late"), full.blockedByCap);
    }

    @Test
    void tierOutcomesMergeAcrossAttempts() throws IOException {
        Flakiness.writeTier(tmp, "regression", List.of(
                new TestResult("a#x", "a", "x", TestResult.Status.FLAKY, "t", "m"),
                new TestResult("a#y", "a", "y", TestResult.Status.PASSED, null, null)));
        Flakiness.writeTier(tmp, "regression", List.of(
                new TestResult("a#y", "a", "y", TestResult.Status.FAILED, "t", "m"),
                new TestResult("a#z", "a", "z", TestResult.Status.SKIPPED, null, null)));
        List<Flakiness.Outcome> all = Flakiness.mergeTiers(tmp);
        assertEquals(2, all.size(), "skipped tests are not outcomes");
        assertEquals("FLAKY", all.get(0).status);
        assertEquals("FAILED", all.get(1).status, "a rerun replaces the earlier outcome");
    }

    @Test
    void settingsHaveDefaults() {
        Flakiness.Settings s = Flakiness.settings(Map.of());
        assertEquals(30, s.window());
        assertEquals(5, s.minRuns());
        assertNull(new Flakiness.Report().find("missing"));
    }

    private static Flakiness.Score score(String id, String tier, int runs, double value) {
        Flakiness.Score s = new Flakiness.Score();
        s.id = id;
        s.tier = tier;
        s.runs = runs;
        s.score = value;
        s.flaky = (int) Math.round(runs * value / 100);
        return s;
    }
}
