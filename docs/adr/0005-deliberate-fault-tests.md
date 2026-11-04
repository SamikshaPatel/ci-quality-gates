# ADR-0005: Why the repo contains deliberate faults

**Status:** Accepted · 2026-11-04

## Context
A quality gate you have never seen fail is a quality gate you are trusting on faith. Each failure path in the contract needs to be shown on demand: an environment outage, a code break in smoke, a flaky critical test, a flake breach, and a full quarantine. That includes during a live demo. Waiting for real flakiness does not work, and a test that fails at random might pass at the moment it matters.

## Decision
- Eight fault tests live in `api-tests/.../faults/DemoFaultTests.java`, each tagged `demo-fault`.
- The root POM excludes `demo-fault` from every run. They run only when a demo scenario is selected (the "Run workflow" button, or `DEMO_SCENARIO` in Jenkins), and each test is enabled only for its own scenario.
- They are **deterministic**. A "flaky" fault test fails on its first attempt and passes on the retry, using a marker file under `target/`. The gate classifies it as flaky every time.
- The `env-down` scenario uses no fault test: it stops the real API container, so the outage is real.

## Consequences
- Every branch of the contract can be demonstrated in about 10 minutes, on cue.
- Normal runs on `main` stay honestly green. The fault tests are test equipment for the gates, not neglected product tests.
