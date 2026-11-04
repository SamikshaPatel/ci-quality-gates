# Evidence

Frozen run reports from real GitHub Actions runs of each demo scenario, recorded on 2026-11-04. GitHub deletes workflow logs and artifacts after a retention period, so the proof of each failure path is kept here as well. Each report links back to its run while that run still exists.

| Scenario | Proves | Verdict | Report |
|---|---|---|---|
| `none` | All gates green: 5 + 9 + 11 tests | ✅ Passed | [run-report](none/run-report.md) |
| `env-down` | API container stopped: smoke fails, 3 failures classified **ENV**, owner DevOps, later gates skipped | ❌ Blocked | [run-report](env-down/run-report.md) |
| `smoke-code` | Wrong expectation in smoke: classified **CODE**, owner PR author | ❌ Blocked | [run-report](smoke-code/run-report.md) |
| `critical-flake` | A critical test passes only on retry and still fails the gate (90%, no retry credit) | ❌ Blocked | [run-report](critical-flake/run-report.md) |
| `flake-breach` | Flake rate 21.4% > 20%: 3 tests quarantined, 3 Jira tickets opened, rerun passes 11/11 | ✅ Passed after quarantine | [run-report](flake-breach/run-report.md) |
| `quarantine-cap` | 6 tests would be quarantined against a cap of 5: quarantine refused, owner QA lead | ❌ Blocked | [run-report](quarantine-cap/run-report.md) |

In the `flake-breach` report the Jira keys are masked, because the project key was stored as a secret during that run. It is now a plain repository variable, so later runs show real ticket links.
