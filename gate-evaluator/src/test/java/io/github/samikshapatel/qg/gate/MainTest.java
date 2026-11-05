package io.github.samikshapatel.qg.gate;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
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
