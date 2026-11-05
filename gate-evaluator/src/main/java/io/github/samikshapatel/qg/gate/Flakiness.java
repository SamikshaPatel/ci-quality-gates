package io.github.samikshapatel.qg.gate;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Flakiness score (ADR-0013): how often each test fails or needs a retry on main, across many runs.
 *
 * One retry in one run is a weak signal; a test that fails 3 runs in 20 on code that already passed
 * review is flaky, whatever any single run says. Only main-branch runs count (nightly, promotion
 * gates, manual runs on main), never PRs, whose failures may be the PR's own bugs, and never demo
 * scenarios or runs where the environment was down.
 */
public final class Flakiness {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** One test's outcome in one run, written per tier and merged into tests.json. */
    public static class Outcome {
        public String id;
        public String tier;
        public String status;

        public Outcome() {
        }

        public Outcome(String id, String tier, String status) {
            this.id = id;
            this.tier = tier;
            this.status = status;
        }
    }

    public static class Score {
        public String id;
        public String tier;
        public int runs;
        public int passed;
        public int failed;
        public int flaky;
        public double score;
        public String lastBad;
    }

    public static class Report {
        public String generatedAt;
        public int window;
        public int minRuns;
        public double quarantineScore;
        public int eligibleRuns;
        public List<Score> tests = new ArrayList<>();

        public Score find(String id) {
            return tests.stream().filter(s -> s.id.equals(id)).findFirst().orElse(null);
        }
    }

    record Settings(int window, int minRuns, double quarantineScore) {
    }

    private Flakiness() {
    }

    @SuppressWarnings("unchecked")
    static Settings settings(Map<String, Object> root) {
        Object section = root.get("flakiness");
        Map<String, Object> f = section instanceof Map ? (Map<String, Object>) section : Map.of();
        return new Settings(intOf(f.get("window"), 30), intOf(f.get("min_runs"), 5),
                f.get("quarantine_score") instanceof Number n ? n.doubleValue() : 10.0);
    }

    /** Merges a tier's outcomes into the tier file; a rerun replaces the same test's earlier outcome. */
    static void writeTier(Path out, String tier, List<TestResult> results) throws IOException {
        Path file = out.resolve(tier + "-tests.json");
        Map<String, Outcome> merged = new LinkedHashMap<>();
        if (Files.exists(file)) {
            for (Outcome o : MAPPER.readValue(file.toFile(), new TypeReference<List<Outcome>>() { })) {
                merged.put(o.id, o);
            }
        }
        for (TestResult r : results) {
            if (r.executed()) {
                merged.put(r.id(), new Outcome(r.id(), tier, r.status().name()));
            }
        }
        MAPPER.writeValue(file.toFile(), new ArrayList<>(merged.values()));
    }

    /** All tiers' outcomes for this run, as tests.json. */
    static List<Outcome> mergeTiers(Path out) throws IOException {
        List<Outcome> all = new ArrayList<>();
        for (String tier : RunReport.ORDER) {
            Path file = out.resolve(tier + "-tests.json");
            if (Files.exists(file)) {
                all.addAll(MAPPER.readValue(file.toFile(), new TypeReference<List<Outcome>>() { }));
            }
        }
        return all;
    }

    /** Scores every test over the newest eligible runs in runs/&lt;folder&gt;/{history-entry,tests}.json. */
    static Report compute(Path runsDir, Settings settings, String now) throws IOException {
        List<RunHistory.Entry> entries = new ArrayList<>();
        Map<String, Path> folders = new LinkedHashMap<>();
        if (Files.isDirectory(runsDir)) {
            try (Stream<Path> dirs = Files.list(runsDir)) {
                for (Path dir : dirs.filter(Files::isDirectory).toList()) {
                    Path entryFile = dir.resolve("history-entry.json");
                    if (Files.exists(entryFile) && Files.exists(dir.resolve("tests.json"))) {
                        RunHistory.Entry e = MAPPER.readValue(entryFile.toFile(), RunHistory.Entry.class);
                        if (eligible(e)) {
                            entries.add(e);
                            folders.put(e.folder, dir);
                        }
                    }
                }
            }
        }
        entries.sort(Comparator.comparing((RunHistory.Entry e) -> e.date).reversed());
        List<RunHistory.Entry> window = entries.stream().limit(settings.window()).toList();

        Map<String, Score> scores = new LinkedHashMap<>();
        for (RunHistory.Entry e : window) {
            List<Outcome> outcomes = MAPPER.readValue(folders.get(e.folder).resolve("tests.json").toFile(),
                    new TypeReference<List<Outcome>>() { });
            for (Outcome o : outcomes) {
                Score s = scores.get(o.id);
                if (s == null) {
                    s = new Score();
                    s.id = o.id;
                    s.tier = o.tier;
                    scores.put(o.id, s);
                }
                s.runs++;
                switch (o.status) {
                    case "PASSED" -> s.passed++;
                    case "FLAKY" -> s.flaky++;
                    case "FAILED" -> s.failed++;
                    default -> s.runs--;
                }
                if (("FLAKY".equals(o.status) || "FAILED".equals(o.status))
                        && (s.lastBad == null || e.date.compareTo(s.lastBad) > 0)) {
                    s.lastBad = e.date;
                }
            }
        }
        Report r = new Report();
        r.generatedAt = now;
        r.window = settings.window();
        r.minRuns = settings.minRuns();
        r.quarantineScore = settings.quarantineScore();
        r.eligibleRuns = window.size();
        for (Score s : scores.values()) {
            s.score = s.runs == 0 ? 0 : Math.round(1000.0 * (s.failed + s.flaky) / s.runs) / 10.0;
            r.tests.add(s);
        }
        r.tests.sort(Comparator.comparingDouble((Score s) -> s.score).reversed().thenComparing(s -> s.id));
        return r;
    }

    /** Main-branch runs only, no demo scenarios, and not runs where the environment itself was down. */
    static boolean eligible(RunHistory.Entry e) {
        boolean main = "main".equals(e.branch) && e.pr == null;
        boolean demo = e.scenario != null && !"none".equals(e.scenario);
        boolean envDown = "FAILED".equals(e.verdict) && "ENV".equals(e.classification);
        return main && !demo && !envDown;
    }

    /** What the nightly score-based quarantine did. */
    public static class ScoreQuarantine {
        public List<QuarantineStore.Entry> added = new ArrayList<>();
        public List<String> blockedByCap = new ArrayList<>();
    }

    /**
     * Nightly: quarantines non-critical tests whose score reached the limit over enough runs,
     * with a ticket each, as long as the quarantine cap allows. Protected tests never qualify.
     */
    static ScoreQuarantine quarantineByScore(Report report, QuarantineStore store, GateConfig config,
                                             Function<TestResult, String> ticketer, String today, String runUrl) {
        ScoreQuarantine result = new ScoreQuarantine();
        for (Score s : report.tests) {
            if (!"regression".equals(s.tier) || s.runs < report.minRuns || s.score < report.quarantineScore
                    || store.find(s.id).isPresent()) {
                continue;
            }
            String className = s.id.contains("#") ? s.id.substring(0, s.id.indexOf('#')) : s.id;
            String method = s.id.contains("#") ? s.id.substring(s.id.indexOf('#') + 1) : s.id;
            String why = "Flakiness score " + GateResult.pct(s.score) + ": failed or flaky in "
                    + (s.failed + s.flaky) + " of " + s.runs + " main runs";
            TestResult test = new TestResult(s.id, className, method, TestResult.Status.FLAKY, "Flakiness score", why);
            if (config.isNeverQuarantine(test)) {
                continue;
            }
            if (store.size() >= config.quarantineMaxTests()) {
                result.blockedByCap.add(s.id);
                continue;
            }
            QuarantineStore.Entry entry = new QuarantineStore.Entry(s.id, today, why, ticketer.apply(test), runUrl);
            store.add(entry);
            result.added.add(entry);
        }
        return result;
    }

    /** One line for a test in a PR's run report: is this failure new, or a known flaky test? */
    static String verdict(Score s, int minRuns) {
        if (s == null || s.runs < minRuns) {
            return "not enough main-branch history yet";
        }
        int bad = s.failed + s.flaky;
        if (bad == 0) {
            return "never failed in " + s.runs + " main runs: likely caused by this change";
        }
        return "failed or flaky in " + bad + " of " + s.runs + " main runs (" + GateResult.pct(s.score)
                + "): known flaky, likely not this change";
    }

    private static int intOf(Object value, int fallback) {
        return value instanceof Number n ? n.intValue() : fallback;
    }
}
