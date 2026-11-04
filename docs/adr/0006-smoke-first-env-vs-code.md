# ADR-0006: Smoke runs first, and every failure is classified ENV or CODE

**Status:** Accepted · 2026-11-04

## Context
When a pipeline goes red, the first question is "is it us or the environment?" Answering it usually takes a person to read logs. Meanwhile slower suites keep running against a broken environment, producing a pile of misleading failures.

## Decision
- Smoke runs first, takes under 2 minutes and allows no retries. If it fails, later gates are skipped.
- The evaluator classifies each failure using `classification.env_patterns` in `quality-gates.yml`. Timeouts, refused connections, 5xx responses and unreachable hosts are **ENV**; anything else is **CODE**.
- The classification picks the owner from the CI contract: ENV goes to DevOps, CODE goes to the PR author, and a mix of both goes to DevOps first.

## Consequences
- DevOps triages environment failures without asking QA. That is the point of a specific failure signal.
- Classification is pattern-based and can be wrong. A wrong call is fixed by adjusting a pattern in config, and the run report always shows the raw failure message too.
