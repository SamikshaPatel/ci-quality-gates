# ADR-0002: Critical path at 100%; non-critical tier at a baseline that tightens

**Status:** Accepted · 2026-11-04

## Context
A single 80% pass-rate gate invites the obvious question: "So one in five tests can fail and you merge?" For login, checkout or creating a booking, the honest answer must be no. But holding every edge-case test to 100% makes the pipeline block on noise and teaches developers to rerun until green.

## Decision
- **Critical tier:** the flows a release cannot break are gated at 100%. That covers the booking lifecycle, the authorisation check, login, purchase and logout.
- **Non-critical tier:** at least 80% of tests passing and at most 20% flaky.
- 80% is a starting baseline, not a target. It tightens as nightly data shows the suite is stable, and every change is recorded in `threshold_history` in `quality-gates.yml`.

## Consequences
- "Can a failing test merge?" has a precise answer: only a non-critical one, only while the tier stays above threshold, and the run report still lists it.
- Choosing a test's tier is a product decision. The test plan records why each test sits in its tier.
