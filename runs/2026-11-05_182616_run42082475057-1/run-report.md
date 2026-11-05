<!-- ci-quality-gates:run-report -->
## Run report

PR #13 · commit `7c13b39` · 2026-11-05 18:26 UTC · trigger: pull_request

**Result: ✅ PASSED — merge allowed**

**Test selection:** scope `full` — Shared, build or pipeline file changed (`.github/actions/setup-env/action.yml`), so the full suite runs (23 changed files).

| Gate | Result | Measured | Threshold |
|---|---|---|---|
| Smoke | ✅ PASS | 5/5 passed (100%) | ≥ 100% pass, no retries |
| Contract | ✅ PASS | 4/4 passed (100%) | ≥ 100% pass, no retries |
| Critical tier | ✅ PASS | 9/9 passed (100%) | ≥ 100% pass, no retry credit |
| Non-critical tier | ✅ PASS | 11/11 passed (100%), flaky 0 (0%) | ≥ 80% pass, ≤ 20% flaky |

**Evidence:** [Run and artifacts (Allure results, Playwright traces, Surefire XML)](https://github.com/SamikshaPatel/ci-quality-gates/actions/runs/42082475057) · [CI contract](https://github.com/SamikshaPatel/ci-quality-gates/blob/7c13b39d2d34a06c366d7ff28fb27becf135a244/CI_CONTRACT.md) · [Gate config](https://github.com/SamikshaPatel/ci-quality-gates/blob/7c13b39d2d34a06c366d7ff28fb27becf135a244/quality-gates.yml) · [Allure report (every test, steps, screenshots)](https://samikshapatel.github.io/ci-quality-gates/runs/2026-11-05_182616_run42082475057-1/allure/) · [Run history](https://samikshapatel.github.io/ci-quality-gates/)
