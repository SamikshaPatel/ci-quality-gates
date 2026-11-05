<!-- ci-quality-gates:run-report -->
## Run report

commit `05b8344` · 2026-11-05 16:46 UTC · trigger: push · promotion stage: `staging`

**Result: ✅ PASSED — merge allowed**

**Test selection:** scope `full` — Not a pull request, or a demo scenario, so the full suite runs.

| Gate | Result | Measured | Threshold |
|---|---|---|---|
| Smoke | ✅ PASS | 5/5 passed (100%) | ≥ 100% pass, no retries |
| Contract | ✅ PASS | 4/4 passed (100%) | ≥ 100% pass, no retries |
| Critical tier | ✅ PASS | 9/9 passed (100%) | ≥ 100% pass, no retry credit |
| Non-critical tier | ✅ PASS | 11/11 passed (100%), flaky 0 (0%) | ≥ 80% pass, ≤ 20% flaky |

**Evidence:** [Run and artifacts (Allure results, Playwright traces, Surefire XML)](https://github.com/SamikshaPatel/ci-quality-gates/actions/runs/42066902314) · [CI contract](https://github.com/SamikshaPatel/ci-quality-gates/blob/05b8344261063839c05d86e81f2e266e8949704c/CI_CONTRACT.md) · [Gate config](https://github.com/SamikshaPatel/ci-quality-gates/blob/05b8344261063839c05d86e81f2e266e8949704c/quality-gates.yml) · [Run history](https://samikshapatel.github.io/ci-quality-gates/)
