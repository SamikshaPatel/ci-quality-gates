package io.github.samikshapatel.qg.gate;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The commands the pipeline actually calls, end to end on temporary files.
 * The environment is an empty map, so no test can reach Jira or the job summary.
 */
class MainTest {

    private static final String API = "io.github.samikshapatel.qg.api.";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path tmp;

    private Path reports(String dirName, String... testcases) throws IOException {
        Path dir = Files.createDirectories(tmp.resolve(dirName));
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<testsuite name=\"t\">\n"
                + String.join("\n", testcases) + "\n</testsuite>\n";
        Files.writeString(dir.resolve("TEST-sample.xml"), xml);
        return dir;
    }

    private static String pass(String cls, String method) {
        return "<testcase name=\"" + method + "()\" classname=\"" + API + cls + "\"/>";
    }

    private static String fail(String cls, String method, String type, String message) {
        return "<testcase name=\"" + method + "()\" classname=\"" + API + cls + "\"><failure type=\"" + type
                + "\" message=\"" + message + "\">trace</failure></testcase>";
    }

    private static String flaky(String cls, String method) {
        return "<testcase name=\"" + method + "()\" classname=\"" + API + cls + "\"><flakyFailure type=\"AssertionError\""
                + " message=\"blip\">trace</flakyFailure></testcase>";
    }

    private Map<String, String> opts(String tier, Path reports) {
        Map<String, String> o = new HashMap<>();
        o.put("tier", tier);
        o.put("reports", reports.toString());
        o.put("config", "../quality-gates.yml");
        o.put("quarantine", tmp.resolve("quarantine.json").toString());
        o.put("out", tmp.resolve("out").toString());
        return o;
    }

    @Test
    void passingSmokeExitsZeroAndWritesResult() throws IOException {
        Path r = reports("smoke", pass("ApiSmokeTest", "ping"), pass("ApiSmokeTest", "auth"));
        assertEquals(Main.PASS, Main.evaluate(opts("smoke", r), Map.of()));
        assertTrue(Files.exists(tmp.resolve("out/smoke.json")));
        assertFalse(Files.exists(tmp.resolve("out/quality-gate-failure.txt")));
    }

    @Test
    void failingSmokeExitsOneAndWritesFailureFile() throws IOException {
        Path r = reports("smoke", pass("ApiSmokeTest", "ping"),
                fail("ApiSmokeTest", "auth", "java.net.ConnectException", "Connection refused"));
        assertEquals(Main.FAIL, Main.evaluate(opts("smoke", r), Map.of()));
        String failure = Files.readString(tmp.resolve("out/quality-gate-failure.txt"));
        assertTrue(failure.contains("classification: ENV"), failure);
    }

    @Test
    void flakeBreachQuarantinesThenRerunPassesAndReportTellsTheStory() throws IOException {
        String[] first = new String[14];
        for (int i = 0; i < 11; i++) {
            first[i] = pass("BookingRegressionTest", "t" + i);
        }
        for (int i = 0; i < 3; i++) {
            first[11 + i] = flaky("BookingRegressionTest", "flaky" + i);
        }
        assertEquals(Main.QUARANTINE_RERUN, Main.evaluate(opts("regression", reports("attempt1", first)), Map.of()));
        assertEquals(3, QuarantineStore.load(tmp.resolve("quarantine.json")).size());

        String[] second = new String[11];
        for (int i = 0; i < 11; i++) {
            second[i] = pass("BookingRegressionTest", "t" + i);
        }
        Map<String, String> rerun = opts("regression", reports("attempt2", second));
        rerun.put("attempt", "2");
        assertEquals(Main.PASS, Main.evaluate(rerun, Map.of()));

        GateResult result = MAPPER.readValue(tmp.resolve("out/regression.json").toFile(), GateResult.class);
        assertEquals(3, result.newlyQuarantined.size(), "attempt 1's quarantine is kept on the final result");
        assertTrue(result.firstAttemptReason.contains("flake rate"), result.firstAttemptReason);

        Path summary = tmp.resolve("summary.md");
        Map<String, String> env = Map.of("GITHUB_STEP_SUMMARY", summary.toString(), "GITHUB_RUN_ID", "7");
        assertEquals(Main.PASS, Main.report(opts("regression", tmp), env));
        String report = Files.readString(tmp.resolve("out/run-report.md"));
        assertTrue(report.contains("### Quarantine"), report);
        assertTrue(report.contains("not created (Jira not configured)"), report);
        assertTrue(Files.readString(summary).contains("## Run report"));
        RunHistory.Entry entry = MAPPER.readValue(tmp.resolve("out/history-entry.json").toFile(), RunHistory.Entry.class);
        assertEquals(3, entry.quarantinedThisRun);
        assertEquals(3, entry.quarantineSize);
    }

    @Test
    void nightlyReviewReleasesAfterCleanStreak() throws IOException {
        QuarantineStore store = QuarantineStore.load(tmp.resolve("quarantine.json"));
        QuarantineStore.Entry e = new QuarantineStore.Entry(API + "BookingRegressionTest#ready",
                java.time.LocalDate.now().toString(), "flaky", null, "run");
        e.greenStreak = 6;
        store.add(e);
        store.save();
        Path r = reports("quarantine", pass("BookingRegressionTest", "ready"));
        assertEquals(Main.PASS, Main.review(opts("regression", r)));
        assertEquals(0, QuarantineStore.load(tmp.resolve("quarantine.json")).size());
        assertTrue(Files.exists(tmp.resolve("out/quarantine-review.json")));
    }

    @Test
    void nightlyReviewFlagsOverdueQuarantine() throws IOException {
        QuarantineStore store = QuarantineStore.load(tmp.resolve("quarantine.json"));
        store.add(new QuarantineStore.Entry(API + "BookingRegressionTest#old", "2020-01-01", "flaky", null, "run"));
        store.save();
        Path r = reports("quarantine", flaky("BookingRegressionTest", "old"));
        assertEquals(Main.OVERDUE, Main.review(opts("regression", r)));
    }

    @Test
    void planWritesTestPlanAndReportShowsSelection() throws IOException {
        Path changed = tmp.resolve("changed.txt");
        Files.writeString(changed, "README.md\ndocs/test-plan.md\n");
        Map<String, String> o = new HashMap<>(opts("smoke", tmp));
        o.put("changed", changed.toString());
        assertEquals(Main.PASS, Main.plan(o));
        assertEquals("docs", MAPPER.readValue(tmp.resolve("out/test-plan.json").toFile(), ImpactPlanner.Plan.class).scope);

        for (String tier : RunReport.ORDER) {
            Map<String, String> skip = new HashMap<>(opts(tier, tmp));
            skip.put("not-selected", "Documentation only");
            assertEquals(Main.PASS, Main.evaluate(skip, Map.of()));
        }
        assertEquals(Main.PASS, Main.report(opts("smoke", tmp), Map.of()));
        String report = Files.readString(tmp.resolve("out/run-report.md"));
        assertTrue(report.contains("**Result: ✅ PASSED — merge allowed**"), report);
        assertTrue(report.contains("**Test selection:** scope `docs`"), report);
        assertTrue(report.contains("| Smoke | ⏭️ NOT SELECTED | not needed for this change |"), report);
        RunHistory.Entry entry = MAPPER.readValue(tmp.resolve("out/history-entry.json").toFile(), RunHistory.Entry.class);
        assertEquals("docs", entry.scope);
        assertEquals("PASSED", entry.verdict);
    }

    @Test
    void failedStabilityCheckBlocksTheRun() throws IOException {
        Path r = reports("smoke", pass("ApiSmokeTest", "ping"));
        Main.evaluate(opts("smoke", r), Map.of());
        Files.writeString(tmp.resolve("out/stability.md"), "### Stability check\n- ❌ `BookingContractTest` failed on run 2 of 3\n");
        Files.writeString(tmp.resolve("out/stability-failed"), "");
        Main.report(opts("smoke", tmp), Map.of());
        String report = Files.readString(tmp.resolve("out/run-report.md"));
        assertTrue(report.contains("**Result: ❌ FAILED — merge blocked**"), report);
        assertTrue(report.contains("failed on run 2 of 3"), report);
        RunHistory.Entry entry = MAPPER.readValue(tmp.resolve("out/history-entry.json").toFile(), RunHistory.Entry.class);
        assertEquals("FAILED", entry.verdict);
    }

    @Test
    void planFullWhenAsked() throws IOException {
        Map<String, String> o = new HashMap<>(opts("smoke", tmp));
        o.put("full", "Nightly run");
        assertEquals(Main.PASS, Main.plan(o));
        assertEquals("full", MAPPER.readValue(tmp.resolve("out/test-plan.json").toFile(), ImpactPlanner.Plan.class).scope);
    }

    @Test
    void allureCategoriesUseTheGateEnvPatterns() throws IOException {
        Path file = tmp.resolve("allure-results/categories.json");
        Map<String, String> o = Map.of("file", file.toString(), "config", "../quality-gates.yml");
        assertEquals(Main.PASS, Main.allureCategories(o));
        com.fasterxml.jackson.databind.JsonNode categories = MAPPER.readTree(file.toFile());
        assertEquals(4, categories.size());
        String envRegex = categories.get(0).get("messageRegex").asText();
        assertTrue(java.util.regex.Pattern.compile(envRegex).matcher("java.net.ConnectException: Connection refused").matches());
        assertFalse(java.util.regex.Pattern.compile(envRegex).matcher("expected: <200> but was: <201>").matches());
        assertTrue(categories.get(3).get("flaky").asBoolean());
    }

    @Test
    void reportShowsFailureHistoryAndWritesTestsJson() throws IOException {
        Path r = reports("smoke", pass("ApiSmokeTest", "ping"), flaky("ApiSmokeTest", "auth"));
        Main.evaluate(opts("smoke", r), Map.of());
        Flakiness.Report baseline = new Flakiness.Report();
        baseline.minRuns = 5;
        baseline.eligibleRuns = 12;
        Flakiness.Score s = new Flakiness.Score();
        s.id = API + "ApiSmokeTest#auth";
        s.runs = 12;
        s.flaky = 3;
        s.score = 25;
        baseline.tests.add(s);
        MAPPER.writeValue(tmp.resolve("out/flakiness-baseline.json").toFile(), baseline);

        Main.report(opts("smoke", tmp), Map.of());
        String report = Files.readString(tmp.resolve("out/run-report.md"));
        assertTrue(report.contains("### Failure history on main"), report);
        assertTrue(report.contains("`ApiSmokeTest.auth` | FLAKY | failed or flaky in 3 of 12 main runs (25%)"), report);
        assertEquals(2, MAPPER.readTree(tmp.resolve("out/tests.json").toFile()).size());
    }

    @Test
    void flakinessAndScoreQuarantineCommands() throws IOException {
        Path runs = Files.createDirectories(tmp.resolve("runs/r1"));
        RunHistory.Entry e = new RunHistory.Entry();
        e.folder = "r1";
        e.date = "2026-11-05T06:00:00Z";
        e.branch = "main";
        e.scenario = "none";
        e.verdict = "PASSED";
        MAPPER.writeValue(runs.resolve("history-entry.json").toFile(), e);
        MAPPER.writeValue(runs.resolve("tests.json").toFile(),
                List.of(new Flakiness.Outcome(API + "BookingRegressionTest#x", "regression", "FLAKY")));
        Path flakinessFile = tmp.resolve("site/flakiness.json");
        assertEquals(Main.PASS, Main.flakiness(Map.of("runs", tmp.resolve("runs").toString(),
                "out", flakinessFile.toString(), "config", "../quality-gates.yml")));
        assertEquals(1, MAPPER.readValue(flakinessFile.toFile(), Flakiness.Report.class).eligibleRuns);

        Map<String, String> o = new HashMap<>(opts("regression", tmp));
        o.put("flakiness", flakinessFile.toString());
        assertEquals(Main.PASS, Main.quarantineByScore(o, Map.of()));
        assertEquals(0, QuarantineStore.load(tmp.resolve("quarantine.json")).size(), "one run is below min_runs");
        assertTrue(Files.exists(tmp.resolve("out/score-quarantine.json")));

        o.put("flakiness", tmp.resolve("missing.json").toString());
        assertEquals(Main.PASS, Main.quarantineByScore(o, Map.of()));
    }

    @Test
    void retriesComeFromConfig() throws IOException {
        Map<String, String> o = Map.of("tier", "regression", "config", "../quality-gates.yml");
        assertEquals(Main.PASS, Main.retries(o));
    }

    @Test
    void optionsParseValuesAndFlags() {
        Map<String, String> o = Main.options(new String[]{"--tier", "smoke", "--dry-run", "--attempt", "2"});
        assertEquals("smoke", o.get("tier"));
        assertEquals("true", o.get("dry-run"));
        assertEquals("2", o.get("attempt"));
    }

    @Test
    void missingTierIsRejected() {
        Map<String, String> o = new HashMap<>(opts("smoke", tmp));
        o.remove("tier");
        assertThrows(IllegalArgumentException.class, () -> Main.evaluate(o, Map.of()));
    }
}
