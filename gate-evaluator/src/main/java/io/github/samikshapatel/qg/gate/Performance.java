package io.github.samikshapatel.qg.gate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

/**
 * Performance gate (ADR-0014): reads k6's summary and applies the limits in the `performance`
 * section of quality-gates.yml. k6 only measures; the verdict, like every other gate's, comes
 * from the evaluator, with a message naming the measured value and the limit.
 */
public final class Performance {

    /** Written to gate-results/performance.json, shown in the run report and the run history. */
    public static class Result {
        public boolean passed;
        public String reason;
        public double p95Ms;
        public double p99Ms;
        public double avgMs;
        public double errorRatePercent;
        public double requestsPerSecond;
        public long requests;
        public double p95LimitMs;
        public double errorRateLimitPercent;
        public String load;
    }

    record Limits(double p95Ms, double errorRatePercent, String vus, String duration) {
    }

    private Performance() {
    }

    @SuppressWarnings("unchecked")
    static Limits limits(Map<String, Object> root) {
        Object section = root.get("performance");
        if (!(section instanceof Map)) {
            throw new IllegalStateException("quality-gates.yml is missing the 'performance' section");
        }
        Map<String, Object> p = (Map<String, Object>) section;
        return new Limits(number(p.get("p95_ms"), 500), number(p.get("error_rate_percent"), 1),
                String.valueOf(p.getOrDefault("vus", 10)), String.valueOf(p.getOrDefault("duration", "30s")));
    }

    static Result evaluate(Path k6Summary, Limits limits) throws IOException {
        JsonNode metrics = new ObjectMapper().readTree(k6Summary.toFile()).path("metrics");
        JsonNode duration = metrics.path("http_req_duration").path("values");
        JsonNode failed = metrics.path("http_req_failed").path("values");
        JsonNode reqs = metrics.path("http_reqs").path("values");

        Result r = new Result();
        r.p95Ms = round(duration.path("p(95)").asDouble());
        r.p99Ms = round(duration.path("p(99)").asDouble());
        r.avgMs = round(duration.path("avg").asDouble());
        r.errorRatePercent = round(100 * failed.path("rate").asDouble());
        r.requestsPerSecond = round(reqs.path("rate").asDouble());
        r.requests = reqs.path("count").asLong();
        r.p95LimitMs = limits.p95Ms();
        r.errorRateLimitPercent = limits.errorRatePercent();
        r.load = limits.vus() + " virtual users for " + limits.duration();

        if (r.requests == 0) {
            r.passed = false;
            r.reason = "Performance gate breached: no requests completed — the API was unreachable";
        } else if (r.p95Ms > limits.p95Ms()) {
            r.passed = false;
            r.reason = "Performance gate breached: p95 response time " + fmt(r.p95Ms) + " ms, above the "
                    + fmt(limits.p95Ms()) + " ms limit";
        } else if (r.errorRatePercent > limits.errorRatePercent()) {
            r.passed = false;
            r.reason = "Performance gate breached: error rate " + GateResult.pct(r.errorRatePercent)
                    + ", above the " + GateResult.pct(limits.errorRatePercent()) + " limit";
        } else {
            r.passed = true;
            r.reason = "Performance within limits: p95 " + fmt(r.p95Ms) + " ms (limit " + fmt(limits.p95Ms())
                    + " ms), errors " + GateResult.pct(r.errorRatePercent);
        }
        return r;
    }

    /** The run report section. */
    static String markdown(Result r) {
        return "### Performance " + (r.passed ? "✅" : "❌") + "\n\n"
                + "**" + r.reason + ".** Load: " + r.load + ", " + r.requests + " requests ("
                + fmt(r.requestsPerSecond) + " per second).\n\n"
                + "| Measure | Value | Limit |\n|---|---|---|\n"
                + "| p95 response time | " + fmt(r.p95Ms) + " ms | ≤ " + fmt(r.p95LimitMs) + " ms |\n"
                + "| p99 response time | " + fmt(r.p99Ms) + " ms | — |\n"
                + "| Average response time | " + fmt(r.avgMs) + " ms | — |\n"
                + "| Error rate | " + GateResult.pct(r.errorRatePercent) + " | ≤ " + GateResult.pct(r.errorRateLimitPercent) + " |\n"
                + (r.passed ? "" : "\n**Owner:** PR author, with DevOps if the environment is slow. "
                + "**Runbook:** `docs/runbooks/performance-failure.md`\n");
    }

    private static double number(Object value, double fallback) {
        return value instanceof Number n ? n.doubleValue() : fallback;
    }

    private static double round(double v) {
        return Math.round(v * 10) / 10.0;
    }

    private static String fmt(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format("%.1f", v);
    }
}
