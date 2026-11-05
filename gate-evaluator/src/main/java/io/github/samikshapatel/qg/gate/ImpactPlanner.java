package io.github.samikshapatel.qg.gate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Impact-based test selection (ADR-0010): which gates and test modules a PR needs, decided from
 * the files it changes and the rules in the `impact` section of quality-gates.yml.
 *
 * <pre>
 * docs only           no test gates (the evaluator's own tests and security still run)
 * API test code only  smoke on everything; contract, critical and regression for the API module
 * UI test code only   smoke on everything; critical and regression for the UI module
 * anything else       the full suite: build files, shared code, gates and pipelines affect everything
 * </pre>
 * Smoke always covers every module: it is cheap, and a change can break a neighbour's basics.
 * New or changed test classes are listed for the stability check, which runs each several times.
 */
public final class ImpactPlanner {

    public static final List<String> ALL_TIERS = RunReport.ORDER;
    static final List<String> ALL_MODULES = List.of("api-tests", "ui-tests");
    private static final Pattern TEST_CLASS = Pattern.compile("^(api-tests|ui-tests)/src/test/java/(.+)\\.java$");

    /** The plan for one run. Serialised to gate-results/test-plan.json. */
    public static class Plan {
        public String scope;
        public String reason;
        public List<String> modules = new ArrayList<>();
        public List<String> selectedTiers = new ArrayList<>();
        public List<String> changedFiles = new ArrayList<>();
        public List<String> stabilityTests = new ArrayList<>();
        public int stabilityRuns;

        public boolean selected(String tier) {
            return selectedTiers.contains(tier);
        }

        /** One line for the run report. */
        public String note() {
            String files = changedFiles.isEmpty() ? "" : " (" + changedFiles.size() + " changed file"
                    + (changedFiles.size() == 1 ? "" : "s") + ")";
            return "scope `" + scope + "` — " + reason + files + ".";
        }
    }

    record Rules(List<Pattern> docs, List<Pattern> api, List<Pattern> ui, int stabilityRuns) {
    }

    private ImpactPlanner() {
    }

    @SuppressWarnings("unchecked")
    static Rules rules(Map<String, Object> root) {
        Object section = root.get("impact");
        if (!(section instanceof Map)) {
            throw new IllegalStateException("quality-gates.yml is missing the 'impact' section");
        }
        Map<String, Object> impact = (Map<String, Object>) section;
        Object runs = impact.getOrDefault("stability_runs", 3);
        return new Rules(globs(impact.get("docs")), globs(impact.get("api")), globs(impact.get("ui")),
                runs instanceof Number n ? n.intValue() : 3);
    }

    /** Everything, for nightly, releases, manual runs and demo scenarios. */
    public static Plan full(String reason, int stabilityRuns) {
        Plan p = new Plan();
        p.scope = "full";
        p.reason = reason;
        p.modules.addAll(ALL_MODULES);
        p.selectedTiers.addAll(ALL_TIERS);
        p.stabilityRuns = stabilityRuns;
        return p;
    }

    /**
     * @param exists tells whether a changed path still exists (deleted tests need no stability check)
     */
    static Plan plan(List<String> changed, Rules rules, Predicate<String> exists) {
        List<String> files = changed.stream().map(String::trim).filter(f -> !f.isEmpty()).toList();
        if (files.isEmpty()) {
            return full("No changed files found, so everything runs to be safe", rules.stabilityRuns());
        }

        boolean api = false;
        boolean ui = false;
        String sharedFile = null;
        for (String f : files) {
            if (matchesAny(f, rules.docs())) {
                continue;
            }
            if (matchesAny(f, rules.api())) {
                api = true;
            } else if (matchesAny(f, rules.ui())) {
                ui = true;
            } else if (sharedFile == null) {
                sharedFile = f;
            }
        }

        Plan p;
        if (sharedFile != null) {
            p = full("Shared, build or pipeline file changed (`" + sharedFile + "`), so the full suite runs",
                    rules.stabilityRuns());
        } else if (api && ui) {
            p = full("Both API and UI test code changed, so the full suite runs", rules.stabilityRuns());
        } else if (api) {
            p = scoped("api", List.of("api-tests"), List.of("smoke", "contract", "critical", "regression"),
                    "Only API test code changed: smoke covers everything; UI critical and regression are not needed",
                    rules.stabilityRuns());
        } else if (ui) {
            p = scoped("ui", List.of("ui-tests"), List.of("smoke", "critical", "regression"),
                    "Only UI test code changed: smoke covers everything; contract and API critical and regression are not needed",
                    rules.stabilityRuns());
        } else {
            p = scoped("docs", List.of(), List.of(),
                    "Documentation only: no test gates needed (evaluator tests and security checks still run)",
                    rules.stabilityRuns());
        }
        p.changedFiles.addAll(files);

        if (!"docs".equals(p.scope)) {
            for (String f : files) {
                Matcher m = TEST_CLASS.matcher(f);
                if (!m.matches() || !exists.test(f)) {
                    continue;
                }
                String className = m.group(2).replace('/', '.');
                String simple = className.substring(className.lastIndexOf('.') + 1);
                if (simple.endsWith("Test") && !simple.startsWith("Base") && !className.contains(".faults.")) {
                    p.stabilityTests.add(m.group(1) + "|" + className);
                }
            }
        }
        return p;
    }

    /** KEY=VALUE lines for $GITHUB_ENV, read by scripts/run-gate.sh and scripts/stability-check.sh. */
    static String envLines(Plan p) {
        return "TEST_SCOPE=" + p.scope + "\n"
                + "SELECTED_TIERS=" + String.join(",", p.selectedTiers) + "\n"
                + "TEST_MODULES=" + String.join(",", p.modules) + "\n"
                + "SCOPE_REASON=" + p.reason.replace('\n', ' ') + "\n"
                + "STABILITY_TESTS=" + String.join(";", p.stabilityTests) + "\n"
                + "STABILITY_RUNS=" + p.stabilityRuns + "\n";
    }

    private static Plan scoped(String scope, List<String> modules, List<String> tiers, String reason, int runs) {
        Plan p = new Plan();
        p.scope = scope;
        p.reason = reason;
        p.modules.addAll(modules);
        p.selectedTiers.addAll(tiers);
        p.stabilityRuns = runs;
        return p;
    }

    private static boolean matchesAny(String path, List<Pattern> patterns) {
        return patterns.stream().anyMatch(p -> p.matcher(path).matches());
    }

    @SuppressWarnings("unchecked")
    private static List<Pattern> globs(Object value) {
        List<Pattern> patterns = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object glob : list) {
                patterns.add(glob(String.valueOf(glob)));
            }
        }
        return patterns;
    }

    /** "docs/**" matches under docs/; a pattern with no slash ("*.md") matches a file name at any depth. */
    static Pattern glob(String glob) {
        StringBuilder regex = new StringBuilder(glob.contains("/") ? "^" : "^(?:.*/)?");
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            if (c == '*' && i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
                regex.append(".*");
                i++;
            } else if (c == '*') {
                regex.append("[^/]*");
            } else if (c == '?') {
                regex.append("[^/]");
            } else if ("\\.[]{}()+-^$|".indexOf(c) >= 0) {
                regex.append('\\').append(c);
            } else {
                regex.append(c);
            }
        }
        return Pattern.compile(regex.append('$').toString());
    }
}
