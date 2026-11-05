<!-- ci-quality-gates:run-report -->
## Run report

PR #12 · commit `35d95d6` · 2026-11-05 15:44 UTC · trigger: pull_request

**Result: ✅ PASSED — merge allowed**

**Test selection:** scope `full` — Shared, build or pipeline file changed (`.github/workflows/pr.yml`), so the full suite runs (8 changed files).

| Gate | Result | Measured | Threshold |
|---|---|---|---|
| Smoke | ✅ PASS | 5/5 passed (100%) | ≥ 100% pass, no retries |
| Contract | ✅ PASS | 4/4 passed (100%) | ≥ 100% pass, no retries |
| Critical tier | ✅ PASS | 9/9 passed (100%) | ≥ 100% pass, no retry credit |
| Non-critical tier | ✅ PASS | 11/11 passed (100%), flaky 0 (0%) | ≥ 80% pass, ≤ 20% flaky |

**Evidence:** [Run and artifacts (Allure results, Playwright traces, Surefire XML)](https://github.com/SamikshaPatel/ci-quality-gates/actions/runs/42057576928) · [CI contract](https://github.com/SamikshaPatel/ci-quality-gates/blob/35d95d6dfbf1e94e583631042e83dadfbeecdba5/CI_CONTRACT.md) · [Gate config](https://github.com/SamikshaPatel/ci-quality-gates/blob/35d95d6dfbf1e94e583631042e83dadfbeecdba5/quality-gates.yml) · [Run history](https://samikshapatel.github.io/ci-quality-gates/)
