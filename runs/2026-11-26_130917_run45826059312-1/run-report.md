<!-- ci-quality-gates:run-report -->
## Run report

PR #21 · commit `2dd5cb1` · 2026-11-26 13:09 UTC · trigger: pull_request

**Result: ✅ PASSED — merge allowed**

**Test selection:** scope `full` — Shared, build or pipeline file changed (`.github/workflows/promote.yml`), so the full suite runs (2 changed files).

| Gate | Result | Measured | Threshold |
|---|---|---|---|
| Smoke | ✅ PASS | 5/5 passed (100%) | ≥ 100% pass, no retries |
| Contract | ✅ PASS | 4/4 passed (100%) | ≥ 100% pass, no retries |
| Critical tier | ✅ PASS | 9/9 passed (100%) | ≥ 100% pass, no retry credit |
| Non-critical tier | ✅ PASS | 11/11 passed (100%), flaky 0 (0%) | ≥ 80% pass, ≤ 20% flaky |

**Evidence:** [Run and artifacts (Allure results, Playwright traces, Surefire XML)](https://github.com/SamikshaPatel/ci-quality-gates/actions/runs/45826059312) · [CI contract](https://github.com/SamikshaPatel/ci-quality-gates/blob/2dd5cb1e56ffdd801e1f32045f20058d4fc1ccfc/CI_CONTRACT.md) · [Gate config](https://github.com/SamikshaPatel/ci-quality-gates/blob/2dd5cb1e56ffdd801e1f32045f20058d4fc1ccfc/quality-gates.yml) · [Allure report (every test, steps, screenshots)](https://samikshapatel.github.io/ci-quality-gates/runs/2026-11-26_130917_run45826059312-1/allure/) · [Run history](https://samikshapatel.github.io/ci-quality-gates/)
