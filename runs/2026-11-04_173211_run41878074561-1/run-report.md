<!-- ci-quality-gates:run-report -->
## Run report

commit `aa29646` · 2026-11-04 17:32 UTC · trigger: workflow_dispatch · demo scenario: `critical-flake`

**Result: ❌ FAILED — merge blocked**

| Gate | Result | Measured | Threshold |
|---|---|---|---|
| Smoke | ✅ PASS | 5/5 passed (100%) | ≥ 100% pass, no retries |
| Critical tier | ❌ FAIL | 9/10 passed (90%) | ≥ 100% pass, no retry credit |
| Non-critical tier | ⏭️ SKIPPED | skipped (earlier gate failed) | — |

### What failed

**Critical gate breached: pass rate 90%, below 100% threshold (1 passed only on retry — no retry credit on the critical path)**

- **Failure classification:** `CODE`
- **Pipeline action:** Build marked failed, PR blocked. Critical tests are never quarantined.
- **Owner:** PR author (QA reviews) — per CI_CONTRACT.md
- **Next step:** Treat the intermittent failure as a real defect: a critical flow that fails once in production fails a customer.
- **Runbook:** [docs/runbooks/critical-failure.md](https://github.com/SamikshaPatel/ci-quality-gates/blob/aa29646c9d1a9fa5bbde6e562484bd142482cd91/docs/runbooks/critical-failure.md)

| Test | Status | Class | First failure |
|---|---|---|---|
| `DemoFaultTests.CriticalFlakeFault.bookingTotalIntermittentlyWrong` | FLAKY | CODE | Simulated intermittent failure: booking total was 0 on first read |

**Evidence:** [Run and artifacts (Allure results, Playwright traces, Surefire XML)](https://github.com/SamikshaPatel/ci-quality-gates/actions/runs/41878074561) · [CI contract](https://github.com/SamikshaPatel/ci-quality-gates/blob/aa29646c9d1a9fa5bbde6e562484bd142482cd91/CI_CONTRACT.md) · [Gate config](https://github.com/SamikshaPatel/ci-quality-gates/blob/aa29646c9d1a9fa5bbde6e562484bd142482cd91/quality-gates.yml) · [Run history](https://samikshapatel.github.io/ci-quality-gates/)
