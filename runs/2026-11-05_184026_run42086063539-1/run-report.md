<!-- ci-quality-gates:run-report -->
## Run report

commit `d5df75b` · 2026-11-05 18:40 UTC · trigger: workflow_dispatch

**Result: ✅ PASSED — merge allowed**

| Gate | Result | Measured | Threshold |
|---|---|---|---|
| Smoke | ✅ PASS | 5/5 passed (100%) | ≥ 100% pass, no retries |
| Contract | ✅ PASS | 4/4 passed (100%) | ≥ 100% pass, no retries |
| Critical tier | ✅ PASS | 9/9 passed (100%) | ≥ 100% pass, no retry credit |
| Non-critical tier | ✅ PASS | 11/11 passed (100%), flaky 0 (0%) | ≥ 80% pass, ≤ 20% flaky |

### Performance ✅

**Performance within limits: p95 12.5 ms (limit 500 ms), errors 0%.** Load: 10 virtual users for 30s, 46722 requests (1556.5 per second).

| Measure | Value | Limit |
|---|---|---|
| p95 response time | 12.5 ms | ≤ 500 ms |
| p99 response time | 15.1 ms | — |
| Average response time | 6.3 ms | — |
| Error rate | 0% | ≤ 1% |

**Evidence:** [Run and artifacts (Allure results, Playwright traces, Surefire XML)](https://github.com/SamikshaPatel/ci-quality-gates/actions/runs/42086063539) · [CI contract](https://github.com/SamikshaPatel/ci-quality-gates/blob/d5df75b98c633bdf185d2d476d5e1470e91fc938/CI_CONTRACT.md) · [Gate config](https://github.com/SamikshaPatel/ci-quality-gates/blob/d5df75b98c633bdf185d2d476d5e1470e91fc938/quality-gates.yml) · [Allure report (every test, steps, screenshots)](https://samikshapatel.github.io/ci-quality-gates/runs/2026-11-05_184026_run42086063539-1/allure/) · [Run history](https://samikshapatel.github.io/ci-quality-gates/)
