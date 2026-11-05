# ADR-0014: A performance gate on Staging and nightly, judged by the evaluator

**Status:** Accepted · 2026-11-05

## Context
Functional gates prove the API answers correctly, not that it answers fast enough under load. Performance regressions creep in one merge at a time and are expensive to find later. But load tests are slow and noisy on shared CI runners, so running them on every PR would blow the PR time budget and produce false alarms.

## Decision
- A **k6** load test (`perf/booking-load.js`) drives the booking API's main paths: list, create and read a booking. It runs with 10 virtual users for 30 seconds.
- It runs **nightly** and as **Gate 5 of the Staging promotion**, the production-like stage. It never runs on PRs.
- k6 only measures. The **gate evaluator** applies the limits in `quality-gates.yml`: p95 under 500 ms and an error rate of at most 1%. A breach fails with the value and the limit, for example: *"p95 response time 612 ms, above the 500 ms limit"*, like every other gate.
- The result appears in the run report and the run history, and the site charts p95 against the limit over time.
- k6 runs from its official container image, pinned to a version.

## Consequences
- A breach on Staging stops the promotion before production.
- Thirty seconds on a shared runner measures trends, not capacity. Absolute numbers vary between runners, which is why the limit is generous and the trend chart matters more than any single run.
- Real capacity testing (ramp-up, soak, spike tests) belongs on a dedicated, production-sized environment, with the same rule: measured by a tool, judged by limits in config.
