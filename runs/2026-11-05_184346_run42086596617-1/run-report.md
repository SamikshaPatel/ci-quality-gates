<!-- ci-quality-gates:run-report -->
## Run report

PR #18 · commit `30dbc87` · 2026-11-05 18:43 UTC · trigger: pull_request

**Result: ✅ PASSED — merge allowed**

**Test selection:** scope `full` — Shared, build or pipeline file changed (`.github/workflows/nightly.yml`), so the full suite runs (21 changed files).

| Gate | Result | Measured | Threshold |
|---|---|---|---|
| Smoke | ✅ PASS | 5/5 passed (100%) | ≥ 100% pass, no retries |
| Contract | ✅ PASS | 4/4 passed (100%) | ≥ 100% pass, no retries |
| Critical tier | ✅ PASS | 9/9 passed (100%) | ≥ 100% pass, no retry credit |
| Non-critical tier | ✅ PASS | 11/11 passed (100%), flaky 0 (0%) | ≥ 80% pass, ≤ 20% flaky |

**Evidence:** [Run and artifacts (Allure results, Playwright traces, Surefire XML)](https://github.com/SamikshaPatel/ci-quality-gates/actions/runs/42086596617) · [CI contract](https://github.com/SamikshaPatel/ci-quality-gates/blob/30dbc87be28c48c53d9556dc9213d6c035ff2145/CI_CONTRACT.md) · [Gate config](https://github.com/SamikshaPatel/ci-quality-gates/blob/30dbc87be28c48c53d9556dc9213d6c035ff2145/quality-gates.yml) · [Allure report (every test, steps, screenshots)](https://samikshapatel.github.io/ci-quality-gates/runs/2026-11-05_184346_run42086596617-1/allure/) · [Run history](https://samikshapatel.github.io/ci-quality-gates/)
