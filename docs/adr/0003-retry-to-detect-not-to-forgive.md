# ADR-0003: Retry to detect flakiness, not to forgive it

**Status:** Accepted · 2026-11-04

## Context
Automatic retries are the usual fix for flaky tests, and they also hide them: a test that fails half the time looks green forever. We need to measure flakiness without pretending it is not there.

## Decision
- **Smoke:** 0 retries. If the service is down, retrying only delays the bad news.
- **Critical and non-critical:** 1 retry. Surefire records a test that fails and then passes as flaky (`flakyFailure` in the XML), and the evaluator counts it separately.
- **Critical gets no retry credit.** A critical test that passes only on retry still fails the gate. A checkout that fails once in CI fails a customer in production.
- **Non-critical:** a flaky pass counts toward the pass rate but also toward the flake rate, which has its own limit.

## Consequences
- Flakiness becomes a number that is reported on every run and capped.
- One retry adds a little runtime on failing tests. That is within the runtime budget.
