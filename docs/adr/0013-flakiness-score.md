# ADR-0013: Flakiness is a score across runs, not a guess from one retry

**Status:** Accepted · 2026-11-05

## Context
Until now, "flaky" meant "failed, then passed on its one retry, in this run". That is a weak signal. A test that fails 1 run in 10 often passes a whole PR run first time and looks healthy. A test that retried once might have hit a one-off network blip. And when a PR fails, nobody can tell whether the failure is new or a test that always does this.

## Decision
- Every run records each test's outcome (`tests.json`: passed, failed, or passed on retry) next to its run report.
- After every run, the evaluator recomputes a **flakiness score** for each test: the share of the newest 30 **main-branch** runs in which it failed or needed a retry. PR runs (whose failures may be the PR's own bugs), demo scenarios and runs where the environment was down never count.
- A score needs at least 5 runs. The site's **Flakiness scores** page lists every test, highest score first.
- **PR run reports** look up each failing or retried test: *"never failed in 20 main runs: likely caused by this change"* versus *"failed or flaky in 3 of 20 main runs (15%): known flaky"*. That turns the first triage question into a lookup.
- **Nightly** quarantines non-critical tests whose score reaches 10%, with a ticket each and within the cap, through the usual review PR. Critical and smoke tests are listed as "fix now" instead: they are never quarantined.
- The single-run rule stays as the immediate safety net: a PR whose flake rate breaches 20% still quarantines on the spot.

## Consequences
- Quarantine decisions rest on history, so a one-off blip no longer quarantines a good test, and a slowly flaky one no longer hides.
- Scores need data: for the first week of nightly runs, most tests show "needs 5 runs".
- At larger scale, teams add a "flip rate" (pass → fail → pass on the same commit) and weight recent runs more heavily; tools such as Develocity and Datadog CI Visibility do this. The rule (measure on known-good code, across runs) is the same.
