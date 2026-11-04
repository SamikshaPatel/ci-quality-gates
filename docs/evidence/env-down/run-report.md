## Run report

commit `d834851` · 2026-11-04 16:37 UTC · trigger: workflow_dispatch · demo scenario: `env-down`

**Result: ❌ FAILED — merge blocked**

| Gate | Result | Measured | Threshold |
|---|---|---|---|
| Smoke | ❌ FAIL | 2/5 passed (40%) | ≥ 100% pass, no retries |
| Critical tier | ⏭️ SKIPPED | skipped (earlier gate failed) | — |
| Non-critical tier | ⏭️ SKIPPED | skipped (earlier gate failed) | — |

### What failed

**Smoke gate breached: pass rate 40%, below 100% threshold (3 failed, classified ENV)**

- **Failure classification:** `ENV`
- **Pipeline action:** Hard stop: build marked failed, PR blocked. Later gates skipped. No quarantine (smoke tests are never quarantined).
- **Owner:** DevOps — per CI_CONTRACT.md
- **Next step:** Check the service containers (docker compose ps / logs) and the SauceDemo site. If healthy, rerun the workflow.
- **Runbook:** [docs/runbooks/smoke-failure.md](https://github.com/SamikshaPatel/ci-quality-gates/blob/d834851547cdc7f67858a6f394a32aa0cc80e23f/docs/runbooks/smoke-failure.md)

| Test | Status | Class | First failure |
|---|---|---|---|
| `ApiSmokeTest.healthCheckReturns201` | FAILED | ENV | Connection refused (connect failed) |
| `ApiSmokeTest.authReturnsToken` | FAILED | ENV | Connection refused (connect failed) |
| `ApiSmokeTest.bookingListReturnsIds` | FAILED | ENV | Connection refused (connect failed) |

**Evidence:** [Run and artifacts (Allure results, Playwright traces, Surefire XML)](https://github.com/SamikshaPatel/ci-quality-gates/actions/runs/41868685298) · [CI contract](https://github.com/SamikshaPatel/ci-quality-gates/blob/d834851547cdc7f67858a6f394a32aa0cc80e23f/CI_CONTRACT.md) · [Gate config](https://github.com/SamikshaPatel/ci-quality-gates/blob/d834851547cdc7f67858a6f394a32aa0cc80e23f/quality-gates.yml)
