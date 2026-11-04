## Run report

commit `54490a4` · 2026-11-04 17:26 UTC · trigger: workflow_dispatch · demo scenario: `flake-breach`

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
| `DemoFaultTests.NonCriticalFlakeFaults.currencyFormattingFlaky` | [CQGP-4](https://samiksha-qa.atlassian.net/browse/CQGP-4) | 2026-11-04 |
| `DemoFaultTests.NonCriticalFlakeFaults.searchResultsOrderFlaky` | [CQGP-5](https://samiksha-qa.atlassian.net/browse/CQGP-5) | 2026-11-04 |
| `DemoFaultTests.NonCriticalFlakeFaults.additionalNeedsTrimFlaky` | [CQGP-6](https://samiksha-qa.atlassian.net/browse/CQGP-6) | 2026-11-04 |

**Evidence:** [Run and artifacts (Allure results, Playwright traces, Surefire XML)](https://github.com/SamikshaPatel/ci-quality-gates/actions/runs/41877149903) · [CI contract](https://github.com/SamikshaPatel/ci-quality-gates/blob/54490a410221546c7c711b87818348345088d512/CI_CONTRACT.md) · [Gate config](https://github.com/SamikshaPatel/ci-quality-gates/blob/54490a410221546c7c711b87818348345088d512/quality-gates.yml)
