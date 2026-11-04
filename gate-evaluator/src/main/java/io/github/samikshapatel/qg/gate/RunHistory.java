package io.github.samikshapatel.qg.gate;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One row of the run-history site: what ran, when, from which trigger, and how each gate did.
 * Every run writes gate-results/history-entry.json; scripts/publish-history.sh files it on the
 * gh-pages branch under runs/&lt;folder&gt;/, and the site lists all entries newest first.
 */
public final class RunHistory {

    private static final DateTimeFormatter FOLDER = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss").withZone(ZoneOffset.UTC);

    public static class Gate {
        public String verdict;
        public int attempt;
        public int executed;
        public int passed;
        public int flaky;
        public double passRate;
        public double flakeRate;
        public double passRateThreshold;
        public Double maxFlakeRate;
    }

    public static class Entry {
        public String id;
        public String folder;
        public String date;
        public String trigger;
        public String scenario;
        public String branch;
        public String pr;
        public String sha;
        public String runUrl;
        public String verdict;
        public String reason;
        public String owner;
        public String classification;
        public Long durationSec;
        public int quarantineSize;
        public int quarantinedThisRun;
        public Map<String, Gate> gates = new LinkedHashMap<>();
    }

    private RunHistory() {
    }

    public static Entry entry(Map<String, GateResult> results, int quarantineSize, Map<String, String> env, Instant now) {
        Entry e = new Entry();
        String runId = env.getOrDefault("GITHUB_RUN_ID", env.getOrDefault("BUILD_NUMBER", "local"));
        String attempt = env.getOrDefault("GITHUB_RUN_ATTEMPT", "1");
        e.id = runId + "-" + attempt;
        e.folder = FOLDER.format(now) + "_run" + e.id;
        e.date = now.toString();
        e.trigger = env.getOrDefault("GITHUB_EVENT_NAME", env.containsKey("JENKINS_URL") ? "jenkins" : "local");
        String scenario = env.getOrDefault("DEMO_SCENARIO", "none");
        e.scenario = scenario.isBlank() ? "none" : scenario;
        String headRef = env.getOrDefault("GITHUB_HEAD_REF", "");
        e.branch = headRef.isBlank() ? env.getOrDefault("GITHUB_REF_NAME", "") : headRef;
        String pr = env.getOrDefault("PR_NUMBER", "");
        e.pr = pr.isBlank() ? null : pr;
        String sha = env.getOrDefault("GITHUB_SHA", "");
        e.sha = sha.length() > 7 ? sha.substring(0, 7) : sha;
        String server = env.get("GITHUB_SERVER_URL");
        String repo = env.get("GITHUB_REPOSITORY");
        e.runUrl = server == null || repo == null ? env.get("BUILD_URL") : server + "/" + repo + "/actions/runs/" + runId;
        e.verdict = RunReport.overall(results);
        e.quarantineSize = quarantineSize;

        String started = env.getOrDefault("RUN_STARTED_AT", "");
        if (started.matches("\\d+")) {
            e.durationSec = now.getEpochSecond() - Long.parseLong(started);
        }

        for (String tier : RunReport.ORDER) {
            GateResult r = results.get(tier);
            if (r == null) {
                continue;
            }
            Gate g = new Gate();
            g.verdict = r.verdict.name();
            g.attempt = r.attempt;
            g.executed = r.executed;
            g.passed = r.effectivePassed;
            g.flaky = r.flaky;
            g.passRate = r.passRate;
            g.flakeRate = r.flakeRate;
            g.passRateThreshold = r.passRateThreshold;
            g.maxFlakeRate = r.maxFlakeRate;
            e.gates.put(tier, g);
            e.quarantinedThisRun += r.newlyQuarantined.size();
            if (e.reason == null && r.verdict == GateResult.Verdict.FAIL) {
                e.reason = r.reason;
                e.owner = r.owner;
                e.classification = r.classification;
            }
        }
        return e;
    }
}
