<!-- ci-quality-gates:run-report -->
## Run report

PR #10 · commit `7cf44aa` · 2026-11-05 15:43 UTC · trigger: pull_request

**Result: ✅ PASSED — merge allowed**

**Test selection:** scope `api` — Only API test code changed: smoke covers everything; UI critical and regression are not needed (1 changed file).

| Gate | Result | Measured | Threshold |
|---|---|---|---|
| Smoke | ✅ PASS | 5/5 passed (100%) | ≥ 100% pass, no retries |
| Contract | ✅ PASS | 4/4 passed (100%) | ≥ 100% pass, no retries |
| Critical tier | ✅ PASS | 5/5 passed (100%) | ≥ 100% pass, no retry credit |
| Non-critical tier | ✅ PASS | 5/5 passed (100%), flaky 0 (0%) | ≥ 80% pass, ≤ 20% flaky |

### Stability check: new or changed tests, 3 runs each, no retries

- ✅ `BookingRegressionTest` (api-tests) passed 3 of 3 runs

**Evidence:** [Run and artifacts (Allure results, Playwright traces, Surefire XML)](https://github.com/SamikshaPatel/ci-quality-gates/actions/runs/42057401623) · [CI contract](https://github.com/SamikshaPatel/ci-quality-gates/blob/7cf44aaa98840d057fbfb8dda6291fcedd37efd1/CI_CONTRACT.md) · [Gate config](https://github.com/SamikshaPatel/ci-quality-gates/blob/7cf44aaa98840d057fbfb8dda6291fcedd37efd1/quality-gates.yml) · [Run history](https://samikshapatel.github.io/ci-quality-gates/)
