package io.github.samikshapatel.qg.gate;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Builds the run report: one page that says what happened, what the pipeline did,
 * and who acts next. Allure says which test failed; this says what to do about it.
 * The same markdown goes to the job summary, the PR comment and docs/evidence/.
 */
public class RunReport {

    public static final String MARKER = "<!-- ci-quality-gates:run-report -->";
    static final List<String> ORDER = List.of("smoke", "critical", "regression");

    private final Map<String, String> env;

    public RunReport(Map<String, String> env) {
        this.env = env;
    }

    public String render(Map<String, GateResult> results, Optional<QuarantineReview.Summary> review) {
        StringBuilder md = new StringBuilder();
        Optional<GateResult> firstFailure = ORDER.stream()
                .map(results::get)
                .filter(r -> r != null && r.verdict == GateResult.Verdict.FAIL)
                .findFirst();
        boolean allRan = ORDER.stream().allMatch(t -> results.containsKey(t) && results.get(t).passed());
        boolean passed = firstFailure.isEmpty() && allRan;

        md.append(MARKER).append('\n');
        md.append("## Run report\n\n");
        md.append(context()).append("\n\n");
        if (passed) {
            md.append("**Result: ✅ PASSED — merge allowed**\n\n");
        } else if (firstFailure.isPresent()) {
            md.append("**Result: ❌ FAILED — merge blocked**\n\n");
        } else {
            md.append("**Result: ⚠️ INCOMPLETE — not every gate reported; merge blocked**\n\n");
        }

        md.append("| Gate | Result | Measured | Threshold |\n|---|---|---|---|\n");
        boolean earlierFailed = false;
        for (String tier : ORDER) {
            GateResult r = results.get(tier);
            String name = label(tier);
            if (r == null) {
                String why = earlierFailed ? "skipped (earlier gate failed)" : "did not report";
                md.append("| ").append(name).append(" | ⏭️ SKIPPED | ").append(why).append(" | — |\n");
                continue;
            }
            String verdict = switch (r.verdict) {
                case PASS -> r.attempt > 1 ? "✅ PASS after quarantine" : "✅ PASS";
                case FAIL -> "❌ FAIL";
                case QUARANTINE_RERUN -> "🔁 QUARANTINED, rerunning";
            };
            md.append("| ").append(name).append(" | ").append(verdict).append(" | ")
                    .append(r.measuredText()).append(" | ").append(r.thresholdText()).append(" |\n");
            if (r.verdict == GateResult.Verdict.FAIL) {
                earlierFailed = true;
            }
        }
        md.append('\n');

        if (firstFailure.isPresent()) {
            appendFailure(md, firstFailure.get());
        }

        GateResult regression = results.get("regression");
        if (regression != null && !regression.newlyQuarantined.isEmpty()) {
            appendQuarantine(md, regression);
        }

        review.ifPresent(s -> appendReview(md, s));

        md.append("**Evidence:** ");
        md.append(link("Run and artifacts (Allure results, Playwright traces, Surefire XML)", runUrl()));
        md.append(" · ").append(link("CI contract", blob("CI_CONTRACT.md")));
        md.append(" · ").append(link("Gate config", blob("quality-gates.yml")));
        md.append('\n');
        return md.toString();
    }

    private void appendFailure(StringBuilder md, GateResult r) {
        md.append("### What failed\n\n");
        md.append("**").append(r.reason).append("**\n\n");
        if (!"NONE".equals(r.classification)) {
            md.append("- **Failure classification:** `").append(r.classification).append("`\n");
        }
        md.append("- **Pipeline action:** ").append(r.action).append('\n');
        md.append("- **Owner:** ").append(r.owner).append(" (per CI_CONTRACT.md)\n");
        md.append("- **Next step:** ").append(r.nextStep).append('\n');
        md.append("- **Runbook:** ").append(link(r.runbook, blob(r.runbook))).append("\n\n");
        if (!r.failures.isEmpty()) {
            md.append("| Test | Status | Class | First failure |\n|---|---|---|---|\n");
            r.failures.stream().limit(10).forEach(f -> md.append("| `").append(shortId(f.id())).append("` | ")
                    .append(f.status()).append(" | ").append(f.classification()).append(" | ")
                    .append(escape(f.message())).append(" |\n"));
            if (r.failures.size() > 10) {
                md.append("\n…and ").append(r.failures.size() - 10).append(" more in the artifacts.\n");
            }
            md.append('\n');
        }
    }

    private void appendQuarantine(StringBuilder md, GateResult r) {
        md.append("### Quarantine\n\n");
        if (r.firstAttemptReason != null) {
            md.append("First attempt: ").append(r.firstAttemptReason).append(". ");
        }
        md.append("These tests were quarantined, ticketed and excluded from the rerun:\n\n");
        md.append("| Test | Ticket | Since |\n|---|---|---|\n");
        for (QuarantineStore.Entry e : r.newlyQuarantined) {
            String ticket = e.jira == null ? "not created (Jira not configured)" : jiraLink(e.jira);
            md.append("| `").append(shortId(e.id)).append("` | ").append(ticket).append(" | ").append(e.since).append(" |\n");
        }
        md.append('\n');
    }

    private void appendReview(StringBuilder md, QuarantineReview.Summary s) {
        md.append("### Quarantine review (nightly)\n\n");
        md.append("- In quarantine: ").append(s.remaining).append(" of cap ").append(s.cap).append('\n');
        if (!s.released.isEmpty()) {
            md.append("- Released after a clean streak: ").append(String.join(", ", s.released.stream().map(RunReport::shortId).toList())).append('\n');
        }
        if (!s.overdue.isEmpty()) {
            md.append("- ⚠️ Over the age limit (").append(s.maxAgeDays).append(" days): ")
                    .append(String.join(", ", s.overdue.stream().map(RunReport::shortId).toList())).append('\n');
        }
        md.append('\n');
    }

    private String context() {
        String event = env.getOrDefault("GITHUB_EVENT_NAME", "local");
        String sha = env.getOrDefault("GITHUB_SHA", "");
        String shortSha = sha.length() > 7 ? sha.substring(0, 7) : sha;
        StringBuilder c = new StringBuilder();
        String pr = env.getOrDefault("PR_NUMBER", "");
        if (!pr.isBlank()) {
            c.append("PR #").append(pr).append(" · ");
        }
        if (!shortSha.isBlank()) {
            c.append("commit `").append(shortSha).append("` · ");
        }
        c.append(ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'")));
        c.append(" · trigger: ").append(event);
        String scenario = env.getOrDefault("DEMO_SCENARIO", "");
        if (!scenario.isBlank() && !"none".equals(scenario)) {
            c.append(" · demo scenario: `").append(scenario).append('`');
        }
        return c.toString();
    }

    private String runUrl() {
        String server = env.get("GITHUB_SERVER_URL");
        String repo = env.get("GITHUB_REPOSITORY");
        String run = env.get("GITHUB_RUN_ID");
        return server == null || repo == null || run == null ? null : server + "/" + repo + "/actions/runs/" + run;
    }

    private String blob(String path) {
        String server = env.get("GITHUB_SERVER_URL");
        String repo = env.get("GITHUB_REPOSITORY");
        String sha = env.get("GITHUB_SHA");
        return server == null || repo == null || sha == null ? path : server + "/" + repo + "/blob/" + sha + "/" + path;
    }

    private String jiraLink(String key) {
        String base = env.getOrDefault("JIRA_BASE_URL", "");
        return base.isBlank() ? key : "[" + key + "](" + (base.endsWith("/") ? base : base + "/") + "browse/" + key + ")";
    }

    private static String link(String text, String url) {
        return url == null ? text : "[" + text + "](" + url + ")";
    }

    static String label(String tier) {
        return switch (tier) {
            case "smoke" -> "Smoke";
            case "critical" -> "Critical tier";
            case "regression" -> "Non-critical tier";
            default -> tier;
        };
    }

    /** io.github...api.ApiSmokeTest#healthCheckReturns201 → ApiSmokeTest.healthCheckReturns201 */
    static String shortId(String id) {
        String cls = id.contains("#") ? id.substring(0, id.indexOf('#')) : id;
        String method = id.contains("#") ? id.substring(id.indexOf('#') + 1) : "";
        String simple = cls.substring(cls.lastIndexOf('.') + 1).replace('$', '.');
        return method.isEmpty() ? simple : simple + "." + method;
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("|", "\\|").replace("\n", " ");
    }

    /** Plain-text breach file for scripts and DevOps: quality-gate-failure.txt. */
    public static String failureFile(GateResult r) {
        StringBuilder t = new StringBuilder();
        t.append("QUALITY GATE BREACHED\n");
        t.append("gate: ").append(r.tier).append('\n');
        t.append("reason: ").append(r.reason).append('\n');
        t.append("classification: ").append(r.classification).append('\n');
        t.append("pass_rate: ").append(r.passRate).append(" (threshold ").append(r.passRateThreshold).append(")\n");
        if (r.maxFlakeRate != null) {
            t.append("flake_rate: ").append(r.flakeRate).append(" (max ").append(r.maxFlakeRate).append(")\n");
        }
        t.append("owner: ").append(r.owner).append('\n');
        t.append("runbook: ").append(r.runbook).append('\n');
        t.append("next_step: ").append(r.nextStep).append('\n');
        for (GateResult.Failure f : r.failures) {
            t.append("test: ").append(f.id()).append(" | ").append(f.status()).append(" | ")
                    .append(f.classification()).append(" | ").append(f.message()).append('\n');
        }
        return t.toString();
    }
}
