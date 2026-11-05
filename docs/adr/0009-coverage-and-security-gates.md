# ADR-0009: The evaluator is tested first, and security checks run on every PR

**Status:** Accepted · 2026-11-05

## Context
Every merge depends on the gate evaluator's verdict, so a bug in the evaluator could let a broken build through or block a good one. "Who tests the tester?" has to have an answer. Separately, test gates check behaviour, not whether the code or its dependencies are safe.

## Decision
- **Coverage gate:** the evaluator's unit tests run before any test gate, and JaCoCo fails the job if they cover less than 80% of its lines. The tests include the command-line entry point (on temporary files, with an empty environment so they never reach Jira) and the Jira client (against a fake server on localhost). The minimum lives in `gate-evaluator/pom.xml`, because this gate runs before the evaluator exists to read `quality-gates.yml`.
- **CodeQL** with the security-extended queries runs on every PR, on `main` and weekly. The ruleset's code scanning rule blocks a merge that introduces an alert.
- **Dependency review** fails a PR that adds a dependency with a known high or critical vulnerability. It is a required status check.
- Secret scanning with push protection is switched on in the repository settings.

## Consequences
- An evaluator change that drops coverage fails with the measured number, the same way the test gates report theirs.
- Coverage is a floor, not a goal: 80% of lines with meaningful assertions beats 100% without them. Raising the floor is a reviewed change, like the other thresholds.
- Security findings arrive in the PR, before merge, instead of in a later audit.
