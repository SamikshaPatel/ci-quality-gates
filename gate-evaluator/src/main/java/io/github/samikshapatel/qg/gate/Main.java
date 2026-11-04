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
 * evaluate --tier smoke|critical|regression --reports dir[,dir] [--attempt 1|2]
 *          exit 0 = gate passed, 1 = gate failed, 3 = flaky tests quarantined: rerun the tier
 * report   writes gate-results/run-report.md and appends it to the GitHub job summary
 * review   nightly quarantine review: green streaks, releases, age alerts (exit 2 = overdue)
 * retries  prints the configured retry count for --tier
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
            case "evaluate" -> evaluate(opts);
            case "report" -> report(opts);
            case "review" -> review(opts);
            case "retries" -> retries(opts);
            default -> {
                System.err.println("Unknown command: " + args[0]);
                yield FAIL;
            }
        };
        System.exit(code);
    }

    static int evaluate(Map<String, String> opts) throws IOException {
        String tier = required(opts, "tier");
        int attempt = Integer.parseInt(opts.getOrDefault("attempt", "1"));
        GateConfig config = GateConfig.load(Path.of(opts.getOrDefault("config", "quality-gates.yml")));
        QuarantineStore quarantine = QuarantineStore.load(Path.of(opts.getOrDefault("quarantine", "quarantine.json")));
        Path out = Path.of(opts.getOrDefault("out", "gate-results"));
        Files.createDirectories(out);

        List<TestResult> results = SurefireParser.parse(reportDirs(opts));
        Map<String, String> env = System.getenv();
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
        };
    }

    static int report(Map<String, String> opts) throws IOException {
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
        String markdown = new RunReport(System.getenv()).render(results, review);
        Files.writeString(out.resolve("run-report.md"), markdown);

        // One summary line per run for the run-history site (newest first, trends over time).
        int quarantineSize = QuarantineStore.load(Path.of(opts.getOrDefault("quarantine", "quarantine.json"))).size();
        RunHistory.Entry entry = RunHistory.entry(results, quarantineSize, System.getenv(), java.time.Instant.now());
        MAPPER.writeValue(out.resolve("history-entry.json").toFile(), entry);

        String summary = System.getenv("GITHUB_STEP_SUMMARY");
        if (summary != null && !summary.isBlank()) {
            Files.writeString(Path.of(summary), markdown + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
        System.out.println(markdown);
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
