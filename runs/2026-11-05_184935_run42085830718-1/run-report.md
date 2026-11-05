<!-- ci-quality-gates:run-report -->
## Run report

PR #16 · commit `c5ea3a0` · 2026-11-05 18:49 UTC · trigger: pull_request

**Result: ✅ PASSED — merge allowed**

**Test selection:** scope `full` — Shared, build or pipeline file changed (`pom.xml`), so the full suite runs (1 changed file).

| Gate | Result | Measured | Threshold |
|---|---|---|---|
| Smoke | ✅ PASS | 5/5 passed (100%) | ≥ 100% pass, no retries |
| Contract | ✅ PASS | 4/4 passed (100%) | ≥ 100% pass, no retries |
| Critical tier | ✅ PASS | 9/9 passed (100%) | ≥ 100% pass, no retry credit |
| Non-critical tier | ✅ PASS | 11/11 passed (100%), flaky 0 (0%) | ≥ 80% pass, ≤ 20% flaky |

**Evidence:** [Run and artifacts (Allure results, Playwright traces, Surefire XML)](https://github.com/SamikshaPatel/ci-quality-gates/actions/runs/42085830718) · [CI contract](https://github.com/SamikshaPatel/ci-quality-gates/blob/c5ea3a0fb7227dded176269135a1dd54cf2bcfc7/CI_CONTRACT.md) · [Gate config](https://github.com/SamikshaPatel/ci-quality-gates/blob/c5ea3a0fb7227dded176269135a1dd54cf2bcfc7/quality-gates.yml) · [Allure report (every test, steps, screenshots)](https://samikshapatel.github.io/ci-quality-gates/runs/2026-11-05_184935_run42085830718-1/allure/) · [Run history](https://samikshapatel.github.io/ci-quality-gates/)
