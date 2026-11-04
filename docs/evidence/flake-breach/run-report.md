## Run report

commit `d834851` · 2026-11-04 16:42 UTC · trigger: workflow_dispatch · demo scenario: `flake-breach`

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
| `DemoFaultTests.NonCriticalFlakeFaults.currencyFormattingFlaky` | Jira ticket 1 (key masked: stored as a secret during this run) | 2026-11-04 |
| `DemoFaultTests.NonCriticalFlakeFaults.searchResultsOrderFlaky` | Jira ticket 2 (key masked: stored as a secret during this run) | 2026-11-04 |
| `DemoFaultTests.NonCriticalFlakeFaults.additionalNeedsTrimFlaky` | Jira ticket 3 (key masked: stored as a secret during this run) | 2026-11-04 |

**Evidence:** [Run and artifacts (Allure results, Playwright traces, Surefire XML)](https://github.com/SamikshaPatel/ci-quality-gates/actions/runs/41869453462) · [CI contract](https://github.com/SamikshaPatel/ci-quality-gates/blob/d834851547cdc7f67858a6f394a32aa0cc80e23f/CI_CONTRACT.md) · [Gate config](https://github.com/SamikshaPatel/ci-quality-gates/blob/d834851547cdc7f67858a6f394a32aa0cc80e23f/quality-gates.yml)
