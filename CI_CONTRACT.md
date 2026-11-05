# CI contract

**A build is green only when the evaluator is trustworthy, the code is safe, and all four test gates pass, in order.** QA owns this contract; Development and DevOps rely on it. Every number below lives in [`quality-gates.yml`](quality-gates.yml), and changing one is a reviewed PR.

## Before the test gates

| Gate | Rule | Where it is enforced |
|---|---|---|
| Evaluator unit tests and coverage | The evaluator's own tests pass and cover at least 80% of its lines ([ADR-0009](docs/adr/0009-coverage-and-security-gates.md)) | JaCoCo in `gate-evaluator/pom.xml`; fails the job before any test gate runs |
| CodeQL | No new security alert from the security-extended queries | `security.yml`, enforced by the ruleset's code scanning rule |
| Dependency review | A PR may not add a dependency with a known high or critical vulnerability | `security.yml`, a required status check |

## What green means

| Order | Gate | Tests | Pass rule | Retries | Blocks merge |
|---|---|---|---|---|---|
| 1 | Smoke | `@Tag("smoke")` | 100% | 0: a retry would hide an outage | Yes, and later gates are skipped |
| 2 | Contract | `@Tag("contract")` | 100%: every API response matches its JSON schema | 0: a schema mismatch is never flaky | Yes, and later gates are skipped |
| 3 | Critical tier | `@Tag("critical")` | 100%, a pass on retry still fails | 1, used only to detect flakiness | Yes |
| 4 | Non-critical tier | `@Tag("regression")` | Pass rate ≥ 80% and flake rate ≤ 20% | 1 | Yes |

- **Pass rate** counts tests that passed on any attempt. **Flake rate** counts tests that passed only on retry.
- A **quarantined** test is skipped by the PR gates, so it no longer affects the gate result. It keeps running nightly.
- The 80% baseline tightens as real run data comes in. Each change is recorded in `threshold_history` in `quality-gates.yml`.

## Which tests a PR runs

A PR runs only the gates its changes can affect ([ADR-0010](docs/adr/0010-impact-based-selection.md)); the rules live in the `impact` section of `quality-gates.yml`.

| Changed files | Gates that run |
|---|---|
| Documentation only | No test gates. Evaluator tests and security checks still run |
| API test code only | Smoke on every module; contract, critical and regression for the API module |
| UI test code only | Smoke on every module; critical and regression for the UI module |
| Both, or anything else | The full suite |

- A gate that is not needed is recorded as **NOT SELECTED** with the reason, never skipped silently.
- New or changed test classes must pass **3 runs in a row with no retries**, or the PR is blocked.
- Nightly, releases, manual runs and demo scenarios always run the full suite.

## Who acts on a failure

The gate evaluator tags each failure **ENV** (timeout, 5xx, unreachable host) or **CODE** (assertion against a healthy environment). The tag picks the owner, so no one has to ask QA.

| Failure | Pipeline action | Owner | Runbook |
|---|---|---|---|
| Smoke, ENV | Build failed, PR blocked, later gates skipped | DevOps | [smoke-failure](docs/runbooks/smoke-failure.md) |
| Smoke, CODE | Build failed, PR blocked, later gates skipped | PR author | [smoke-failure](docs/runbooks/smoke-failure.md) |
| Contract, schema mismatch | Build failed, PR blocked, later gates skipped | PR author (API provider) | [contract-failure](docs/runbooks/contract-failure.md) |
| Critical tier, any failure or flake | Build failed, PR blocked | PR author, QA reviews | [critical-failure](docs/runbooks/critical-failure.md) |
| Non-critical, pass rate below threshold | Build failed, PR blocked | PR author | [quality-gate-failure](docs/runbooks/quality-gate-failure.md) |
| Non-critical, flake rate over limit | Flaky tests quarantined, ticket opened per test, gate reruns | QA, with the test's author | [quality-gate-failure](docs/runbooks/quality-gate-failure.md) |
| Stability check, a new or changed test failed a run | Build failed, PR blocked | PR author | [ADR-0010](docs/adr/0010-impact-based-selection.md) |
| Quarantine cap would be exceeded | Quarantine refused, build failed, PR blocked | QA lead | [quality-gate-failure](docs/runbooks/quality-gate-failure.md) |

## Quarantine rules

| Rule | Value |
|---|---|
| Who can be quarantined | Non-critical tests only. Smoke and critical tests never are, and neither are classes matching `never_quarantine` |
| Cap | 5 tests. Above it, the PR is blocked until quarantined tests are fixed |
| Ticket | One Jira ticket per quarantined test, opened automatically |
| Exit | 7 consecutive clean nightly runs |
| Age limit | 14 days. The nightly run fails and names overdue tests |
| Visibility | On a PR, the bot commits the quarantine change to the branch, so it shows in the PR diff, and posts a quarantine notice comment. Nightly changes (releases from quarantine) arrive as a review PR, never a direct push to `main` |

## What every failure must say

A failure message names **the gate, the measured value and the threshold**, for example:

> Quality gate breached: flake rate 21.4%, above 20% limit (3 test(s) passed only on retry)

Every run produces a [run report](README.md#the-run-report) in the job summary and as a PR comment. On a breach, it also writes `gate-results/quality-gate-failure.txt` for scripts.

## Runtime budget

Smoke must finish in under 2 minutes, and the whole PR pipeline in under 10. If new tests would push a PR run past 10 minutes, they move to the nightly run instead.
