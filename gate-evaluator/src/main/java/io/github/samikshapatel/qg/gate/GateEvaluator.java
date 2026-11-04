package io.github.samikshapatel.qg.gate;

import io.github.samikshapatel.qg.gate.Classifier.Kind;
import io.github.samikshapatel.qg.gate.TestResult.Status;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Applies quality-gates.yml to one tier's results. Pure decision logic: no I/O, so every
 * branch of the contract is covered by unit tests (GateEvaluatorTest).
 *
 * <pre>
 * smoke       100% pass, no retries. Failures tagged ENV or CODE; owner follows the tag.
 * critical    100% pass; a pass on retry still fails (no retry credit). Never quarantined.
 * regression  pass rate ≥ threshold (flaky passes count), and flake rate ≤ max.
 *             Flake breach on attempt 1 → quarantine the flaky tests and rerun, unless a
 *             protected test is flaky or the quarantine cap would be exceeded.
 * </pre>
 */
public class GateEvaluator {

    private final GateConfig config;

    public GateEvaluator(GateConfig config) {
        this.config = config;
    }

    /**
     * @param ticketer creates a defect ticket for a newly quarantined test and returns its key (or null)
     */
    public GateResult evaluate(String tier, List<TestResult> results, int attempt, QuarantineStore quarantine,
                               Function<TestResult, String> ticketer, String today, String runUrl) {
        GateResult r = new GateResult();
        r.tier = tier;
        r.attempt = attempt;
        count(r, results);

        if (r.executed == 0) {
            r.verdict = GateResult.Verdict.FAIL;
            r.classification = Kind.ENV.name();
            r.reason = tierLabel(tier) + " gate breached: no tests executed — the suite did not start";
            r.action = "Build marked failed, PR blocked.";
            setOwner(r, config.owner("smoke_env"));
            r.nextStep = "Check the job log for a build or setup failure (dependencies, browser install, service start-up), then rerun.";
            return r;
        }

        return switch (tier) {
            case "smoke" -> smoke(r, results);
            case "critical" -> critical(r, results);
            case "regression" -> regression(r, results, attempt, quarantine, ticketer, today, runUrl);
            default -> throw new IllegalArgumentException("Unknown tier: " + tier);
        };
    }

    private GateResult smoke(GateResult r, List<TestResult> results) {
        r.passRateThreshold = config.smokePassRate();
        List<TestResult> failures = results.stream()
                .filter(t -> t.status() == Status.FAILED || t.status() == Status.FLAKY).toList();
        if (r.passRate >= config.smokePassRate() && failures.isEmpty()) {
            return pass(r);
        }
        Kind kind = Classifier.overall(failures, config.envPatterns());
        r.verdict = GateResult.Verdict.FAIL;
        r.classification = kind.name();
        addFailures(r, failures);
        r.reason = "Smoke gate breached: pass rate " + GateResult.pct(r.passRate) + ", below "
                + GateResult.pct(config.smokePassRate()) + " threshold (" + failures.size() + " failed, classified " + kind + ")";
        r.action = "Hard stop: build marked failed, PR blocked. Later gates skipped. No quarantine (smoke tests are never quarantined).";
        switch (kind) {
            case ENV -> {
                setOwner(r, config.owner("smoke_env"));
                r.nextStep = "Check the service containers (docker compose ps / logs) and the SauceDemo site. If healthy, rerun the workflow.";
            }
            case CODE -> {
                setOwner(r, config.owner("smoke_code"));
                r.nextStep = "Reproduce locally with: mvn -pl api-tests,ui-tests test -Dgroups=smoke -Dtier=smoke — then fix the change in this PR.";
            }
            default -> {
                GateConfig.Owner env = config.owner("smoke_env");
                GateConfig.Owner code = config.owner("smoke_code");
                r.owner = env.owner() + " (ENV failures) and " + code.owner() + " (CODE failures)";
                r.runbook = env.runbook();
                r.nextStep = "DevOps checks the environment first; once it is healthy, the PR author fixes the CODE failures.";
            }
        }
        return r;
    }

    private GateResult critical(GateResult r, List<TestResult> results) {
        r.passRateThreshold = config.criticalPassRate();
        List<TestResult> failures = results.stream()
                .filter(t -> t.status() == Status.FAILED
                        || (config.criticalFlakyCountsAsFailure() && t.status() == Status.FLAKY))
                .toList();
        double effectivePassRate = r.executed == 0 ? 0 : 100.0 * (r.executed - failures.size()) / r.executed;
        r.passRate = round(effectivePassRate);
        r.effectivePassed = r.executed - failures.size();
        if (failures.isEmpty()) {
            return pass(r);
        }
        r.verdict = GateResult.Verdict.FAIL;
        r.classification = Classifier.overall(failures, config.envPatterns()).name();
        addFailures(r, failures);
        long flakyCount = failures.stream().filter(t -> t.status() == Status.FLAKY).count();
        r.reason = "Critical gate breached: pass rate " + GateResult.pct(r.passRate) + ", below "
                + GateResult.pct(config.criticalPassRate()) + " threshold"
                + (flakyCount > 0 ? " (" + flakyCount + " passed only on retry — no retry credit on the critical path)" : "");
        r.action = "Build marked failed, PR blocked. Critical tests are never quarantined.";
        setOwner(r, config.owner("critical"));
        r.nextStep = flakyCount > 0
                ? "Treat the intermittent failure as a real defect: a critical flow that fails once in production fails a customer."
                : "Fix before merge: the change in this PR breaks a critical business flow.";
        return r;
    }

    private GateResult regression(GateResult r, List<TestResult> results, int attempt, QuarantineStore quarantine,
                                  Function<TestResult, String> ticketer, String today, String runUrl) {
        r.passRateThreshold = config.noncriticalPassRate();
        r.maxFlakeRate = config.noncriticalMaxFlakeRate();
        List<TestResult> hardFailures = results.stream().filter(t -> t.status() == Status.FAILED).toList();
        List<TestResult> flaky = results.stream().filter(t -> t.status() == Status.FLAKY).toList();
        flaky.forEach(t -> r.flakyTests.add(t.id()));

        if (r.passRate < config.noncriticalPassRate()) {
            r.verdict = GateResult.Verdict.FAIL;
            r.classification = Classifier.overall(hardFailures, config.envPatterns()).name();
            addFailures(r, hardFailures);
            r.reason = "Quality gate breached: pass rate " + GateResult.pct(r.passRate) + ", below "
                    + GateResult.pct(config.noncriticalPassRate()) + " threshold";
            r.action = "Build marked failed, PR blocked. Hard failures are not quarantined — quarantine is only for flakiness.";
            setOwner(r, config.owner("noncritical_failure"));
            r.nextStep = "Fix the failing tests or the change that broke them.";
            return r;
        }

        if (r.flakeRate <= config.noncriticalMaxFlakeRate()) {
            pass(r);
            if (!hardFailures.isEmpty()) {
                addFailures(r, hardFailures);
                r.action += " " + hardFailures.size() + " test(s) failed but the pass rate stays within threshold; tracked for follow-up.";
            }
            return r;
        }

        String breach = "Quality gate breached: flake rate " + GateResult.pct(r.flakeRate) + ", above "
                + GateResult.pct(config.noncriticalMaxFlakeRate()) + " limit (" + flaky.size() + " test(s) passed only on retry)";
        addFailures(r, flaky);

        if (attempt > 1) {
            r.verdict = GateResult.Verdict.FAIL;
            r.reason = breach + " after quarantine and rerun";
            r.action = "Build marked failed, PR blocked. Flakiness persisted after quarantine.";
            setOwner(r, config.owner("noncritical_flake"));
            r.nextStep = "Investigate the newly flaky tests; the environment may be degraded.";
            return r;
        }

        List<TestResult> protectedFlaky = flaky.stream().filter(config::isNeverQuarantine).toList();
        if (!protectedFlaky.isEmpty()) {
            r.verdict = GateResult.Verdict.FAIL;
            r.reason = breach + "; " + protectedFlaky.size() + " of them on the never-quarantine list";
            r.action = "Build marked failed, PR blocked. Protected tests cannot be quarantined.";
            setOwner(r, config.owner("noncritical_flake"));
            r.nextStep = "Fix the flaky protected test(s): " + ids(protectedFlaky);
            return r;
        }

        List<TestResult> toQuarantine = flaky.stream().filter(t -> quarantine.find(t.id()).isEmpty()).toList();
        int after = quarantine.size() + toQuarantine.size();
        if (after > config.quarantineMaxTests()) {
            r.verdict = GateResult.Verdict.FAIL;
            r.reason = breach + "; quarantine cap reached: " + after + " tests would be quarantined, cap is "
                    + config.quarantineMaxTests();
            r.action = "Quarantine refused, build marked failed, PR blocked.";
            setOwner(r, config.owner("quarantine_cap"));
            r.nextStep = "Quarantine is full. Fix or remove quarantined tests before more can be quarantined — "
                    + "an uncapped quarantine is where failing tests go to hide.";
            return r;
        }

        for (TestResult t : toQuarantine) {
            String ticket = ticketer.apply(t);
            QuarantineStore.Entry entry = new QuarantineStore.Entry(t.id(), today,
                    "Passed only on retry: " + t.shortMessage(), ticket, runUrl);
            quarantine.add(entry);
            r.newlyQuarantined.add(entry);
        }
        r.verdict = GateResult.Verdict.QUARANTINE_RERUN;
        r.reason = breach;
        r.action = "Quarantined " + toQuarantine.size() + " flaky test(s), opened ticket(s), rerunning the gate without them.";
        setOwner(r, config.owner("noncritical_flake"));
        r.nextStep = "Fix each quarantined test via its ticket. A test leaves quarantine after "
                + config.quarantineExitStreak() + " consecutive clean nightly runs.";
        return r;
    }

    private GateResult pass(GateResult r) {
        r.verdict = GateResult.Verdict.PASS;
        r.action = "Gate passed.";
        return r;
    }

    private static void count(GateResult r, List<TestResult> results) {
        for (TestResult t : results) {
            switch (t.status()) {
                case PASSED -> r.passed++;
                case FAILED -> r.failed++;
                case FLAKY -> r.flaky++;
                case SKIPPED -> r.skipped++;
            }
        }
        r.executed = r.passed + r.failed + r.flaky;
        r.effectivePassed = r.passed + r.flaky;
        r.passRate = r.executed == 0 ? 0 : round(100.0 * (r.passed + r.flaky) / r.executed);
        r.flakeRate = r.executed == 0 ? 0 : round(100.0 * r.flaky / r.executed);
    }

    private void addFailures(GateResult r, List<TestResult> failures) {
        for (TestResult t : failures) {
            r.failures.add(new GateResult.Failure(t.id(), t.status().name(),
                    Classifier.classify(t, config.envPatterns()).name(), t.shortMessage()));
        }
    }

    private static void setOwner(GateResult r, GateConfig.Owner owner) {
        r.owner = owner.owner();
        r.runbook = owner.runbook();
    }

    private static String ids(List<TestResult> tests) {
        List<String> ids = new ArrayList<>();
        tests.forEach(t -> ids.add(t.id()));
        return String.join(", ", ids);
    }

    static String tierLabel(String tier) {
        return switch (tier) {
            case "smoke" -> "Smoke";
            case "critical" -> "Critical";
            case "regression" -> "Quality";
            default -> tier;
        };
    }

    private static double round(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
