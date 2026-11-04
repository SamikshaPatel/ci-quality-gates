<!-- ci-quality-gates:run-report -->
## Run report

commit `aa29646` · 2026-11-04 17:32 UTC · trigger: workflow_dispatch · demo scenario: `quarantine-cap`

**Result: ❌ FAILED — merge blocked**

| Gate | Result | Measured | Threshold |
|---|---|---|---|
| Smoke | ✅ PASS | 5/5 passed (100%) | ≥ 100% pass, no retries |
| Critical tier | ✅ PASS | 9/9 passed (100%) | ≥ 100% pass, no retry credit |
| Non-critical tier | ❌ FAIL | 17/17 passed (100%), flaky 6 (35.3%) | ≥ 80% pass, ≤ 20% flaky |

### What failed

**Quality gate breached: flake rate 35.3%, above 20% limit (6 test(s) passed only on retry); quarantine cap reached: 6 tests would be quarantined, cap is 5**

- **Pipeline action:** Quarantine refused, build marked failed, PR blocked.
- **Owner:** QA lead — per CI_CONTRACT.md
- **Next step:** Quarantine is full. Fix or remove quarantined tests before more can be quarantined — an uncapped quarantine is where failing tests go to hide.
- **Runbook:** [docs/runbooks/quality-gate-failure.md](https://github.com/SamikshaPatel/ci-quality-gates/blob/aa29646c9d1a9fa5bbde6e562484bd142482cd91/docs/runbooks/quality-gate-failure.md)

| Test | Status | Class | First failure |
|---|---|---|---|
| `DemoFaultTests.NonCriticalFlakeFaults.dateLocaleFlaky` | FLAKY | CODE | Simulated flaky failure (date-locale): fails first attempt, passes on retry |
| `DemoFaultTests.NonCriticalFlakeFaults.currencyFormattingFlaky` | FLAKY | CODE | Simulated flaky failure (currency-format): fails first attempt, passes on retry |
| `DemoFaultTests.NonCriticalFlakeFaults.searchResultsOrderFlaky` | FLAKY | CODE | Simulated flaky failure (search-order): fails first attempt, passes on retry |
| `DemoFaultTests.NonCriticalFlakeFaults.paginationBoundaryFlaky` | FLAKY | CODE | Simulated flaky failure (pagination): fails first attempt, passes on retry |
| `DemoFaultTests.NonCriticalFlakeFaults.additionalNeedsTrimFlaky` | FLAKY | CODE | Simulated flaky failure (needs-trim): fails first attempt, passes on retry |
| `DemoFaultTests.NonCriticalFlakeFaults.cacheHeaderFlaky` | FLAKY | CODE | Simulated flaky failure (cache-header): fails first attempt, passes on retry |

**Evidence:** [Run and artifacts (Allure results, Playwright traces, Surefire XML)](https://github.com/SamikshaPatel/ci-quality-gates/actions/runs/41878092347) · [CI contract](https://github.com/SamikshaPatel/ci-quality-gates/blob/aa29646c9d1a9fa5bbde6e562484bd142482cd91/CI_CONTRACT.md) · [Gate config](https://github.com/SamikshaPatel/ci-quality-gates/blob/aa29646c9d1a9fa5bbde6e562484bd142482cd91/quality-gates.yml) · [Run history](https://samikshapatel.github.io/ci-quality-gates/)
