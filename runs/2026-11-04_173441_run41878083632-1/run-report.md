<!-- ci-quality-gates:run-report -->
## Run report

commit `aa29646` · 2026-11-04 17:34 UTC · trigger: workflow_dispatch · demo scenario: `flake-breach`

**Result: ✅ PASSED — merge allowed**

| Gate | Result | Measured | Threshold |
|---|---|---|---|
| Smoke | ✅ PASS | 5/5 passed (100%) | ≥ 100% pass, no retries |
| Critical tier | ✅ PASS | 9/9 passed (100%) | ≥ 100% pass, no retry credit |
| Non-critical tier | ✅ PASS after quarantine | 11/11 passed (100%), flaky 0 (0%) | ≥ 80% pass, ≤ 20% flaky |

### Quarantine

First attempt: Quality gate breached: flake rate 21.4%, above 20% limit (3 test(s) passed only on retry). These tests were quarantined, ticketed and excluded from the rerun:

| Test | Ticket | Since |
|---|---|---|
| `DemoFaultTests.NonCriticalFlakeFaults.currencyFormattingFlaky` | [CQGP-7](https://samiksha-qa.atlassian.net/browse/CQGP-7) | 2026-11-04 |
| `DemoFaultTests.NonCriticalFlakeFaults.searchResultsOrderFlaky` | [CQGP-8](https://samiksha-qa.atlassian.net/browse/CQGP-8) | 2026-11-04 |
| `DemoFaultTests.NonCriticalFlakeFaults.additionalNeedsTrimFlaky` | [CQGP-9](https://samiksha-qa.atlassian.net/browse/CQGP-9) | 2026-11-04 |

**Evidence:** [Run and artifacts (Allure results, Playwright traces, Surefire XML)](https://github.com/SamikshaPatel/ci-quality-gates/actions/runs/41878083632) · [CI contract](https://github.com/SamikshaPatel/ci-quality-gates/blob/aa29646c9d1a9fa5bbde6e562484bd142482cd91/CI_CONTRACT.md) · [Gate config](https://github.com/SamikshaPatel/ci-quality-gates/blob/aa29646c9d1a9fa5bbde6e562484bd142482cd91/quality-gates.yml) · [Run history](https://samikshapatel.github.io/ci-quality-gates/)
