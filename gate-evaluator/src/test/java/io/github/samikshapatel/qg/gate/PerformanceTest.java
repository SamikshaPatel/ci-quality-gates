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
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerformanceTest {

    private static final Performance.Limits LIMITS = new Performance.Limits(500, 1, "10", "30s");

    @TempDir
    Path tmp;

    /** The parts of a k6 handleSummary() export the gate reads. */
    private Path summary(double p95, double errorRate, long count) throws IOException {
        String json = """
                {"metrics": {
                  "http_req_duration": {"type": "trend", "values": {"avg": 120.44, "p(95)": %s, "p(99)": 812.3}},
                  "http_req_failed": {"type": "rate", "values": {"rate": %s}},
                  "http_reqs": {"type": "counter", "values": {"count": %d, "rate": 98.7}}
                }}""".formatted(p95, errorRate, count);
        Path file = tmp.resolve("k6-summary.json");
        Files.writeString(file, json);
        return file;
    }

    @Test
    void withinLimitsPasses() throws IOException {
        Performance.Result r = Performance.evaluate(summary(231.56, 0.0, 2961), LIMITS);
        assertTrue(r.passed);
        assertEquals(231.6, r.p95Ms);
        assertEquals(2961, r.requests);
        assertTrue(r.reason.startsWith("Performance within limits: p95 231.6 ms (limit 500 ms)"), r.reason);
    }

    @Test
    void slowP95BreachesWithValueAndLimit() throws IOException {
        Performance.Result r = Performance.evaluate(summary(612.0, 0.0, 2000), LIMITS);
        assertFalse(r.passed);
        assertEquals("Performance gate breached: p95 response time 612 ms, above the 500 ms limit", r.reason);
        assertTrue(Performance.markdown(r).contains("| p95 response time | 612 ms | ≤ 500 ms |"));
        assertTrue(Performance.markdown(r).contains("Owner:"));
    }

    @Test
    void errorRateBreaches() throws IOException {
        Performance.Result r = Performance.evaluate(summary(200, 0.035, 2000), LIMITS);
        assertFalse(r.passed);
        assertEquals("Performance gate breached: error rate 3.5%, above the 1% limit", r.reason);
    }

    @Test
    void noRequestsMeansTheApiWasUnreachable() throws IOException {
        Performance.Result r = Performance.evaluate(summary(0, 1.0, 0), LIMITS);
        assertFalse(r.passed);
        assertTrue(r.reason.contains("unreachable"));
    }

    @Test
    void limitsComeFromConfig() throws IOException {
        Map<String, Object> root;
        try (var reader = Files.newBufferedReader(Path.of("../quality-gates.yml"))) {
            root = new org.yaml.snakeyaml.Yaml().load(reader);
        }
        Performance.Limits l = Performance.limits(root);
        assertEquals(500, l.p95Ms());
        assertEquals("30s", l.duration());
    }

    @Test
    void commandsWriteResultAndExitOnBreach() throws IOException {
        Map<String, String> o = new HashMap<>();
        o.put("summary", summary(612, 0, 2000).toString());
        o.put("out", tmp.resolve("out").toString());
        o.put("config", "../quality-gates.yml");
        assertEquals(Main.FAIL, Main.performance(o));
        Performance.Result r = new ObjectMapper().readValue(tmp.resolve("out/performance.json").toFile(), Performance.Result.class);
        assertFalse(r.passed);
        assertEquals(Main.PASS, Main.performanceLoad(Map.of("config", "../quality-gates.yml")));
    }

    @Test
    void breachFailsTheRunReportAndHistory() throws IOException {
        Path out = Files.createDirectories(tmp.resolve("gr"));
        Map<String, String> o = new HashMap<>();
        o.put("summary", summary(612, 0, 2000).toString());
        o.put("out", out.toString());
        o.put("config", "../quality-gates.yml");
        Main.performance(o);
        o.put("quarantine", tmp.resolve("quarantine.json").toString());
        Main.report(o, Map.of());
        String report = Files.readString(out.resolve("run-report.md"));
        assertTrue(report.contains("**Result: ❌ FAILED — merge blocked**"), report);
        assertTrue(report.contains("### Performance ❌"), report);
        RunHistory.Entry e = new ObjectMapper().readValue(out.resolve("history-entry.json").toFile(), RunHistory.Entry.class);
        assertEquals("FAILED", e.verdict);
        assertEquals(612.0, e.performance.p95Ms);
    }
}
