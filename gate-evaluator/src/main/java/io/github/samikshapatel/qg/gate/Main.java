package io.github.samikshapatel.qg.gate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Command line for the pipeline (GitHub Actions and Jenkins call the same commands).
 *
 * <pre>
 * evaluate --tier smoke|contract|critical|regression --reports dir[,dir] [--attempt 1|2]
 *          exit 0 = gate passed, 1 = gate failed, 3 = flaky tests quarantined: rerun the tier
 * report   writes gate-results/run-report.md and appends it to the GitHub job summary
 * review   nightly quarantine review: green streaks, releases, age alerts (exit 2 = overdue)
 * retries  prints the configured retry count for --tier
 * plan     impact analysis: --changed file-list | --full "reason"; writes test-plan.json, prints KEY=VALUE for $GITHUB_ENV
 *          evaluate --not-selected "reason" records a gate that this change did not need
 * flakiness          scores every test over recent main-branch runs: --runs runs-dir --out flakiness.json
 * quarantine-by-score  nightly: quarantines tests at or over the score limit: --flakiness file
 * allure-categories  writes Allure's categories.json (ENV, CODE, flaky) from the gate config: --file
 * </pre>
 * Common options: --config quality-gates.yml --quarantine quarantine.json --out gate-results
 */
public final class Main {

    static final int PASS = 0;
    static final int FAIL = 1;
    static final int OVERDUE = 2;
    static final int QUARANTINE_RERUN = 3;

    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private Main() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.err.println("usage: gate-evaluator evaluate|report|review [options]");
            System.exit(FAIL);
        }
        Map<String, String> opts = options(Arrays.copyOfRange(args, 1, args.length));
        int code = switch (args[0]) {
            case "evaluate" -> evaluate(opts, System.getenv());
            case "report" -> report(opts, System.getenv());
            case "review" -> review(opts);
            case "retries" -> retries(opts);
            case "plan" -> plan(opts);
            case "allure-categories" -> allureCategories(opts);
            case "flakiness" -> flakiness(opts);
            case "quarantine-by-score" -> quarantineByScore(opts, System.getenv());
            default -> {
                System.err.println("Unknown command: " + args[0]);
                yield FAIL;
            }
        };
        System.exit(code);
    }

    /** The environment is passed in, not read globally, so tests never reach real Jira or the job summary. */
    static int evaluate(Map<String, String> opts, Map<String, String> env) throws IOException {
        String tier = required(opts, "tier");
        Path out = Path.of(opts.getOrDefault("out", "gate-results"));
        if (opts.containsKey("not-selected")) {
            Files.createDirectories(out);
            GateResult skipped = GateResult.notSelected(tier, opts.get("not-selected"));
            MAPPER.writeValue(out.resolve(tier + ".json").toFile(), skipped);
            System.out.println("[gate] " + RunReport.label(tier) + ": NOT SELECTED — " + skipped.reason);
            return PASS;
        }
        int attempt = Integer.parseInt(opts.getOrDefault("attempt", "1"));
        GateConfig config = GateConfig.load(Path.of(opts.getOrDefault("config", "quality-gates.yml")));
        QuarantineStore quarantine = QuarantineStore.load(Path.of(opts.getOrDefault("quarantine", "quarantine.json")));
        Files.createDirectories(out);

        List<TestResult> results = SurefireParser.parse(reportDirs(opts));
        String runUrl = runUrl(env);
        JiraClient jira = new JiraClient(env);

        GateResult result = new GateEvaluator(config).evaluate(tier, results, attempt, quarantine,
                t -> jira.createQuarantineTicket(t, runUrl), LocalDate.now().toString(), runUrl);

        Path resultFile = out.resolve(tier + ".json");
        if (attempt > 1 && Files.exists(resultFile)) {
            // Keep the story of attempt 1 (what was quarantined, and why) on the final result.
            GateResult first = MAPPER.readValue(resultFile.toFile(), GateResult.class);
            result.newlyQuarantined.addAll(0, first.newlyQuarantined);
            result.firstAttemptReason = first.reason;
        }
        MAPPER.writeValue(resultFile.toFile(), result);
        Flakiness.writeTier(out, tier, results);

        if (result.verdict == GateResult.Verdict.QUARANTINE_RERUN) {
            quarantine.save();
        }
        if (result.verdict == GateResult.Verdict.FAIL) {
            Files.writeString(out.resolve("quality-gate-failure.txt"), RunReport.failureFile(result));
        }

        System.out.println("[gate] " + RunReport.label(tier) + " (attempt " + attempt + "): " + result.verdict
                + " — " + result.measuredText() + (result.reason == null ? "" : " — " + result.reason));
        return switch (result.verdict) {
            case PASS -> PASS;
            case FAIL -> FAIL;
            case QUARANTINE_RERUN -> QUARANTINE_RERUN;
            case NOT_SELECTED -> PASS;
        };
    }

    static int report(Map<String, String> opts, Map<String, String> env) throws IOException {
        Path out = Path.of(opts.getOrDefault("out", "gate-results"));
        Files.createDirectories(out);
        Map<String, GateResult> results = new LinkedHashMap<>();
        for (String tier : RunReport.ORDER) {
            Path file = out.resolve(tier + ".json");
            if (Files.exists(file)) {
                results.put(tier, MAPPER.readValue(file.toFile(), GateResult.class));
            }
        }
        Optional<QuarantineReview.Summary> review = Optional.empty();
        Path reviewFile = out.resolve("quarantine-review.json");
        if (Files.exists(reviewFile)) {
            review = Optional.of(MAPPER.readValue(reviewFile.toFile(), QuarantineReview.Summary.class));
        }
        Optional<ImpactPlanner.Plan> plan = Optional.empty();
        Path planFile = out.resolve("test-plan.json");
        if (Files.exists(planFile)) {
            plan = Optional.of(MAPPER.readValue(planFile.toFile(), ImpactPlanner.Plan.class));
        }
        Path stabilityFile = out.resolve("stability.md");
        String stability = Files.exists(stabilityFile) ? Files.readString(stabilityFile) : null;
        boolean stabilityFailed = Files.exists(out.resolve("stability-failed"));

        List<Flakiness.Outcome> outcomes = Flakiness.mergeTiers(out);
        MAPPER.writeValue(out.resolve("tests.json").toFile(), outcomes);

        // One summary line per run for the run-history site (newest first, trends over time).
        int quarantineSize = QuarantineStore.load(Path.of(opts.getOrDefault("quarantine", "quarantine.json"))).size();
        RunHistory.Entry entry = RunHistory.entry(results, quarantineSize, env, java.time.Instant.now());
        entry.scope = plan.map(p -> p.scope).orElse("full");
        entry.allure = Files.exists(out.resolve("allure/index.html"));
        if (stabilityFailed) {
            entry.verdict = "FAILED";
            entry.reason = "Stability check failed: a new or changed test did not pass every run";
            entry.owner = "PR author";
        }
        MAPPER.writeValue(out.resolve("history-entry.json").toFile(), entry);

        String markdown = new RunReport(env)
                .withSelection(plan.map(ImpactPlanner.Plan::note).orElse(null))
                .withStability(stability, stabilityFailed)
                .withAllure(entry.allure ? "runs/" + entry.folder + "/allure/" : null)
                .withSection(historySection(out, outcomes))
                .withSection(scoreQuarantineSection(out))
                .render(results, review);
        Files.writeString(out.resolve("run-report.md"), markdown);

        String summary = env.get("GITHUB_STEP_SUMMARY");
        if (summary != null && !summary.isBlank()) {
            Files.writeString(Path.of(summary), markdown + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
        System.out.println(markdown);
        return PASS;
    }

    /** For each test that failed or needed a retry in this run: has it done that on main before? */
    static String historySection(Path out, List<Flakiness.Outcome> outcomes) throws IOException {
        List<Flakiness.Outcome> bad = outcomes.stream()
                .filter(o -> "FAILED".equals(o.status) || "FLAKY".equals(o.status)).toList();
        Path file = out.resolve("flakiness-baseline.json");
        if (bad.isEmpty() || !Files.exists(file)) {
            return null;
        }
        Flakiness.Report baseline = MAPPER.readValue(file.toFile(), Flakiness.Report.class);
        StringBuilder md = new StringBuilder("### Failure history on main\n\n");
        md.append("| Test | This run | History over the last ").append(baseline.eligibleRuns).append(" main runs |\n|---|---|---|\n");
        bad.stream().limit(15).forEach(o -> md.append("| `").append(RunReport.shortId(o.id)).append("` | ")
                .append(o.status).append(" | ").append(Flakiness.verdict(baseline.find(o.id), baseline.minRuns))
                .append(" |\n"));
        return md.toString();
    }

    static String scoreQuarantineSection(Path out) throws IOException {
        Path file = out.resolve("score-quarantine.json");
        if (!Files.exists(file)) {
            return null;
        }
        Flakiness.ScoreQuarantine q = MAPPER.readValue(file.toFile(), Flakiness.ScoreQuarantine.class);
        if (q.added.isEmpty() && q.blockedByCap.isEmpty()) {
            return null;
        }
        StringBuilder md = new StringBuilder("### Quarantined by flakiness score (nightly)\n\n");
        for (QuarantineStore.Entry e : q.added) {
            md.append("- `").append(RunReport.shortId(e.id)).append("`: ").append(e.reason)
                    .append(e.jira == null ? "" : " (" + e.jira + ")").append('\n');
        }
        for (String id : q.blockedByCap) {
            md.append("- ⚠️ `").append(RunReport.shortId(id)).append("` qualifies, but quarantine is at its cap\n");
        }
        return md.toString();
    }

    static int flakiness(Map<String, String> opts) throws IOException {
        Map<String, Object> root;
        try (var reader = Files.newBufferedReader(Path.of(opts.getOrDefault("config", "quality-gates.yml")))) {
            root = new org.yaml.snakeyaml.Yaml().load(reader);
        }
        Flakiness.Report report = Flakiness.compute(Path.of(required(opts, "runs")), Flakiness.settings(root),
                java.time.Instant.now().toString());
        Path file = Path.of(required(opts, "out"));
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        MAPPER.writeValue(file.toFile(), report);
        long flaky = report.tests.stream().filter(s -> s.runs >= report.minRuns && s.score >= report.quarantineScore).count();
        System.out.println("[gate] Flakiness: " + report.tests.size() + " tests over " + report.eligibleRuns
                + " main runs; " + flaky + " at or over " + GateResult.pct(report.quarantineScore));
        return PASS;
    }

    static int quarantineByScore(Map<String, String> opts, Map<String, String> env) throws IOException {
        Path file = Path.of(required(opts, "flakiness"));
        Path out = Path.of(opts.getOrDefault("out", "gate-results"));
        Files.createDirectories(out);
        if (!Files.exists(file)) {
            System.out.println("[gate] No flakiness scores yet.");
            return PASS;
        }
        GateConfig config = GateConfig.load(Path.of(opts.getOrDefault("config", "quality-gates.yml")));
        QuarantineStore store = QuarantineStore.load(Path.of(opts.getOrDefault("quarantine", "quarantine.json")));
        Flakiness.Report report = MAPPER.readValue(file.toFile(), Flakiness.Report.class);
        JiraClient jira = new JiraClient(env);
        String runUrl = runUrl(env);
        Flakiness.ScoreQuarantine result = Flakiness.quarantineByScore(report, store, config,
                t -> jira.createQuarantineTicket(t, runUrl), LocalDate.now().toString(), runUrl);
        if (!result.added.isEmpty()) {
            store.save();
        }
        MAPPER.writeValue(out.resolve("score-quarantine.json").toFile(), result);
        System.out.println("[gate] Quarantined by score: " + result.added.size() + ", blocked by cap: " + result.blockedByCap.size());
        return PASS;
    }

    static int review(Map<String, String> opts) throws IOException {
        GateConfig config = GateConfig.load(Path.of(opts.getOrDefault("config", "quality-gates.yml")));
        QuarantineStore store = QuarantineStore.load(Path.of(opts.getOrDefault("quarantine", "quarantine.json")));
        Path out = Path.of(opts.getOrDefault("out", "gate-results"));
        Files.createDirectories(out);
        List<TestResult> results = SurefireParser.parse(reportDirs(opts));
        QuarantineReview.Summary summary = QuarantineReview.review(store, results, config, LocalDate.now());
        store.save();
        MAPPER.writeValue(out.resolve("quarantine-review.json").toFile(), summary);
        System.out.println("[gate] Quarantine review: " + summary.remaining + " remaining, released "
                + summary.released + ", overdue " + summary.overdue);
        return summary.overdue.isEmpty() ? PASS : OVERDUE;
    }

    /**
     * Allure groups failures into categories by message pattern. They are generated from the same
     * ENV patterns the gates use, so the report and the gate verdict can never disagree.
     */
    static int allureCategories(Map<String, String> opts) throws IOException {
        GateConfig config = GateConfig.load(Path.of(opts.getOrDefault("config", "quality-gates.yml")));
        Path file = Path.of(required(opts, "file"));
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        MAPPER.writeValue(file.toFile(), allureCategories(config));
        return PASS;
    }

    static List<Map<String, Object>> allureCategories(GateConfig config) {
        String env = config.envPatterns().stream().map(java.util.regex.Pattern::pattern)
                .collect(java.util.stream.Collectors.joining("|"));
        return List.of(
                Map.of("name", "Environment failures (ENV): owner DevOps",
                        "matchedStatuses", List.of("failed", "broken"),
                        "messageRegex", "(?is).*(" + env + ").*"),
                Map.of("name", "Environment failures (ENV): owner DevOps",
                        "matchedStatuses", List.of("failed", "broken"),
                        "traceRegex", "(?is).*(" + env + ").*"),
                Map.of("name", "Product defects (CODE): owner PR author",
                        "matchedStatuses", List.of("failed", "broken")),
                Map.of("name", "Flaky: passed on retry",
                        "matchedStatuses", List.of("passed"),
                        "flaky", true));
    }

    static int plan(Map<String, String> opts) throws IOException {
        Path out = Path.of(opts.getOrDefault("out", "gate-results"));
        Files.createDirectories(out);
        Map<String, Object> root;
        try (var reader = Files.newBufferedReader(Path.of(opts.getOrDefault("config", "quality-gates.yml")))) {
            root = new org.yaml.snakeyaml.Yaml().load(reader);
        }
        ImpactPlanner.Rules rules = ImpactPlanner.rules(root);
        ImpactPlanner.Plan plan;
        if (opts.containsKey("full")) {
            plan = ImpactPlanner.full(opts.get("full"), rules.stabilityRuns());
        } else {
            List<String> changed = Files.readAllLines(Path.of(required(opts, "changed")));
            plan = ImpactPlanner.plan(changed, rules, f -> Files.exists(Path.of(f)));
        }
        MAPPER.writeValue(out.resolve("test-plan.json").toFile(), plan);
        System.out.print(ImpactPlanner.envLines(plan));
        return PASS;
    }

    /** Prints the retry count for a tier, so scripts take it from quality-gates.yml, not a hard-coded flag. */
    @SuppressWarnings("unchecked")
    static int retries(Map<String, String> opts) throws IOException {
        String tier = required(opts, "tier");
        String section = "regression".equals(tier) ? "noncritical" : tier;
        try (var reader = Files.newBufferedReader(Path.of(opts.getOrDefault("config", "quality-gates.yml")))) {
            Map<String, Object> root = new org.yaml.snakeyaml.Yaml().load(reader);
            Object value = ((Map<String, Object>) root.get(section)).getOrDefault("retries", 0);
            System.out.println(value);
        }
        return PASS;
    }

    private static List<Path> reportDirs(Map<String, String> opts) {
        return Arrays.stream(required(opts, "reports").split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).map(Path::of).toList();
    }

    private static String runUrl(Map<String, String> env) {
        String server = env.get("GITHUB_SERVER_URL");
        String repo = env.get("GITHUB_REPOSITORY");
        String run = env.get("GITHUB_RUN_ID");
        if (server != null && repo != null && run != null) {
            return server + "/" + repo + "/actions/runs/" + run;
        }
        return env.getOrDefault("BUILD_URL", "local run");
    }

    static Map<String, String> options(String[] args) {
        Map<String, String> opts = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("--")) {
                String key = args[i].substring(2);
                String value = (i + 1 < args.length && !args[i + 1].startsWith("--")) ? args[++i] : "true";
                opts.put(key, value);
            }
        }
        return opts;
    }

    private static String required(Map<String, String> opts, String key) {
        String value = opts.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing --" + key);
        }
        return value;
    }
}
