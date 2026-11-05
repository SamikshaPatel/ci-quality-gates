package io.github.samikshapatel.qg.gate;

import java.util.ArrayList;
import java.util.List;

/**
 * The verdict of one gate on one attempt. Serialised to gate-results/&lt;tier&gt;.json so the
 * run report can be assembled from every gate that ran.
 */
public class GateResult {

    /** NOT_SELECTED: impact analysis decided this change cannot affect the gate's tests (ADR-0010). */
    public enum Verdict { PASS, FAIL, QUARANTINE_RERUN, NOT_SELECTED }

    public String tier;
    public Verdict verdict;
    public int attempt = 1;
    public int executed;
    public int passed;
    public int failed;
    public int flaky;
    public int skipped;
    /** Tests counted as passing by this gate (critical gives flaky tests no credit). */
    public int effectivePassed;
    public double passRate;
    public double flakeRate;
    public double passRateThreshold;
    public Double maxFlakeRate;
    public String classification = "NONE";
    /** One line naming the gate, the measured value and the threshold. */
    public String reason;
    public String action;
    public String owner;
    public String runbook;
    public String nextStep;
    public List<Failure> failures = new ArrayList<>();
    public List<String> flakyTests = new ArrayList<>();
    public List<QuarantineStore.Entry> newlyQuarantined = new ArrayList<>();
    /** Verdict of the first attempt, when the gate passed only after quarantine and rerun. */
    public String firstAttemptReason;

    public record Failure(String id, String status, String classification, String message) {
    }

    /** A gate that was not needed for this change does not block it. */
    public boolean passed() {
        return verdict == Verdict.PASS || verdict == Verdict.NOT_SELECTED;
    }

    public static GateResult notSelected(String tier, String reason) {
        GateResult r = new GateResult();
        r.tier = tier;
        r.verdict = Verdict.NOT_SELECTED;
        r.reason = reason;
        r.action = "Not run: impact analysis found nothing in this change that these tests cover.";
        return r;
    }

    public String measuredText() {
        if (verdict == Verdict.NOT_SELECTED) {
            return "not needed for this change";
        }
        String base = effectivePassed + "/" + executed + " passed (" + pct(passRate) + ")";
        if (maxFlakeRate != null) {
            base += ", flaky " + flaky + " (" + pct(flakeRate) + ")";
        }
        return base;
    }

    public String thresholdText() {
        if (verdict == Verdict.NOT_SELECTED) {
            return "—";
        }
        String base = "≥ " + pct(passRateThreshold) + " pass";
        if (maxFlakeRate != null) {
            base += ", ≤ " + pct(maxFlakeRate) + " flaky";
        } else if ("smoke".equals(tier) || "contract".equals(tier) || "critical".equals(tier)) {
            base += "critical".equals(tier) ? ", no retry credit" : ", no retries";
        }
        return base;
    }

    static String pct(double value) {
        return (value == Math.rint(value) ? String.valueOf((long) value) : String.format("%.1f", value)) + "%";
    }
}
