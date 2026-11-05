<!-- ci-quality-gates:run-report -->
## Run report

PR #11 · commit `7d0afaf` · 2026-11-05 15:43 UTC · trigger: pull_request

**Result: ✅ PASSED — merge allowed**

**Test selection:** scope `ui` — Only UI test code changed: smoke covers everything; contract and API critical and regression are not needed (1 changed file).

| Gate | Result | Measured | Threshold |
|---|---|---|---|
| Smoke | ✅ PASS | 5/5 passed (100%) | ≥ 100% pass, no retries |
| Contract | ⏭️ NOT SELECTED | not needed for this change | — |
| Critical tier | ✅ PASS | 4/4 passed (100%) | ≥ 100% pass, no retry credit |
| Non-critical tier | ✅ PASS | 6/6 passed (100%), flaky 0 (0%) | ≥ 80% pass, ≤ 20% flaky |

### Stability check: new or changed tests, 3 runs each, no retries

- ✅ `StoreRegressionTest` (ui-tests) passed 3 of 3 runs

**Evidence:** [Run and artifacts (Allure results, Playwright traces, Surefire XML)](https://github.com/SamikshaPatel/ci-quality-gates/actions/runs/42057412147) · [CI contract](https://github.com/SamikshaPatel/ci-quality-gates/blob/7d0afafb7f14a21321f4999a4f75ab07370fd8ab/CI_CONTRACT.md) · [Gate config](https://github.com/SamikshaPatel/ci-quality-gates/blob/7d0afafb7f14a21321f4999a4f75ab07370fd8ab/quality-gates.yml) · [Run history](https://samikshapatel.github.io/ci-quality-gates/)
