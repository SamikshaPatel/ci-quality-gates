<!-- ci-quality-gates:run-report -->
## Run report

PR #19 · commit `b44f5c4` · 2026-11-05 18:58 UTC · trigger: pull_request

**Result: ✅ PASSED — merge allowed**

**Test selection:** scope `full` — Shared, build or pipeline file changed (`.github/workflows/grafana.yml`), so the full suite runs (33 changed files).

| Gate | Result | Measured | Threshold |
|---|---|---|---|
| Smoke | ✅ PASS | 5/5 passed (100%) | ≥ 100% pass, no retries |
| Contract | ✅ PASS | 4/4 passed (100%) | ≥ 100% pass, no retries |
| Critical tier | ✅ PASS | 9/9 passed (100%) | ≥ 100% pass, no retry credit |
| Non-critical tier | ✅ PASS | 11/11 passed (100%), flaky 0 (0%) | ≥ 80% pass, ≤ 20% flaky |

**Evidence:** [Run and artifacts (Allure results, Playwright traces, Surefire XML)](https://github.com/SamikshaPatel/ci-quality-gates/actions/runs/42087531359) · [CI contract](https://github.com/SamikshaPatel/ci-quality-gates/blob/b44f5c479e311bfcb1e38b8bc9b8834a16ed1038/CI_CONTRACT.md) · [Gate config](https://github.com/SamikshaPatel/ci-quality-gates/blob/b44f5c479e311bfcb1e38b8bc9b8834a16ed1038/quality-gates.yml) · [Allure report (every test, steps, screenshots)](https://samikshapatel.github.io/ci-quality-gates/runs/2026-11-05_185850_run42087531359-1/allure/) · [Run history](https://samikshapatel.github.io/ci-quality-gates/)
