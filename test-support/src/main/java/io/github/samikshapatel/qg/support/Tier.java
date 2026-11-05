package io.github.samikshapatel.qg.support;

/**
 * JUnit tag names for the gate tiers. Every real test carries exactly one of
 * SMOKE, CRITICAL or REGRESSION; the pipeline runs each tier as its own gate.
 * DEMO_FAULT marks deliberate fault tests that only run in demo scenarios (ADR-0005).
 */
public final class Tier {
    public static final String SMOKE = "smoke";
    public static final String CONTRACT = "contract";
    public static final String CRITICAL = "critical";
    public static final String REGRESSION = "regression";
    public static final String DEMO_FAULT = "demo-fault";

    private Tier() {
    }
}
