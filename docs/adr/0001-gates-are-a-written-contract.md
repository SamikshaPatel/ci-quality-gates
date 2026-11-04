# ADR-0001: Quality gates are a written contract, enforced from config

**Status:** Accepted · 2026-11-04

## Context
On many teams "is the build good?" depends on whoever reads the test results that day. Thresholds hide inside workflow YAML, a red build sends DevOps to ask QA what it means, and moving to another CI tool means rebuilding the rules from memory.

## Decision
- [`CI_CONTRACT.md`](../../CI_CONTRACT.md) defines what green means. [`quality-gates.yml`](../../quality-gates.yml) holds every threshold, retry count, cap and owner.
- One Java gate evaluator applies the config. Maven never decides the verdict: tests run with `-Dmaven.test.failure.ignore`, and the evaluator's exit code fails or passes the step.
- GitHub Actions and Jenkins call the same scripts (`scripts/run-gate.sh`), so the contract does not depend on the CI tool.

## Consequences
- Changing a threshold is a reviewed PR to one file, with a recorded reason.
- The gate logic has unit tests (`GateEvaluatorTest`) covering every branch of the contract.
- This adds a module to maintain, about 600 lines of Java.
