<!-- ci-quality-gates:run-report -->
## Run report

PR #19 · commit `ce41cbd` · 2026-11-05 19:14 UTC · trigger: pull_request

**Result: ✅ PASSED — merge allowed**

**Test selection:** scope `full` — Shared, build or pipeline file changed (`.github/workflows/grafana.yml`), so the full suite runs (33 changed files).

| Gate | Result | Measured | Threshold |
|---|---|---|---|
| Smoke | ✅ PASS | 5/5 passed (100%) | ≥ 100% pass, no retries |
| Contract | ✅ PASS | 4/4 passed (100%) | ≥ 100% pass, no retries |
| Critical tier | ✅ PASS | 9/9 passed (100%) | ≥ 100% pass, no retry credit |
| Non-critical tier | ✅ PASS | 11/11 passed (100%), flaky 0 (0%) | ≥ 80% pass, ≤ 20% flaky |

**Evidence:** [Run and artifacts (Allure results, Playwright traces, Surefire XML)](https://github.com/SamikshaPatel/ci-quality-gates/actions/runs/42090049639) · [CI contract](https://github.com/SamikshaPatel/ci-quality-gates/blob/ce41cbd4b59aeebf36c5a89784e8ad87c2c0f67c/CI_CONTRACT.md) · [Gate config](https://github.com/SamikshaPatel/ci-quality-gates/blob/ce41cbd4b59aeebf36c5a89784e8ad87c2c0f67c/quality-gates.yml) · [Allure report (every test, steps, screenshots)](https://samikshapatel.github.io/ci-quality-gates/runs/2026-11-05_191417_run42090049639-1/allure/) · [Run history](https://samikshapatel.github.io/ci-quality-gates/)
