<!-- ci-quality-gates:run-report -->
## Run report

PR #5 · commit `26c34ce` · 2026-11-05 14:19 UTC · trigger: pull_request · demo scenario: `flake-breach`

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
| `DemoFaultTests.NonCriticalFlakeFaults.currencyFormattingFlaky` | [CQGP-10](https://samiksha-qa.atlassian.net/browse/CQGP-10) | 2026-11-05 |
| `DemoFaultTests.NonCriticalFlakeFaults.searchResultsOrderFlaky` | [CQGP-11](https://samiksha-qa.atlassian.net/browse/CQGP-11) | 2026-11-05 |
| `DemoFaultTests.NonCriticalFlakeFaults.additionalNeedsTrimFlaky` | [CQGP-12](https://samiksha-qa.atlassian.net/browse/CQGP-12) | 2026-11-05 |

**Evidence:** [Run and artifacts (Allure results, Playwright traces, Surefire XML)](https://github.com/SamikshaPatel/ci-quality-gates/actions/runs/42043588455) · [CI contract](https://github.com/SamikshaPatel/ci-quality-gates/blob/26c34ce29cfb6926697fbdca0073995951df9145/CI_CONTRACT.md) · [Gate config](https://github.com/SamikshaPatel/ci-quality-gates/blob/26c34ce29cfb6926697fbdca0073995951df9145/quality-gates.yml) · [Run history](https://samikshapatel.github.io/ci-quality-gates/)
