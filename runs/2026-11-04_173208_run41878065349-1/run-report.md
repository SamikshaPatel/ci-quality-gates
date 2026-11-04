<!-- ci-quality-gates:run-report -->
## Run report

commit `aa29646` · 2026-11-04 17:32 UTC · trigger: workflow_dispatch · demo scenario: `smoke-code`

**Result: ❌ FAILED — merge blocked**

| Gate | Result | Measured | Threshold |
|---|---|---|---|
| Smoke | ❌ FAIL | 5/6 passed (83.3%) | ≥ 100% pass, no retries |
| Critical tier | ⏭️ SKIPPED | skipped (earlier gate failed) | — |
| Non-critical tier | ⏭️ SKIPPED | skipped (earlier gate failed) | — |

### What failed

**Smoke gate breached: pass rate 83.3%, below 100% threshold (1 failed, classified CODE)**

- **Failure classification:** `CODE`
- **Pipeline action:** Hard stop: build marked failed, PR blocked. Later gates skipped. No quarantine (smoke tests are never quarantined).
- **Owner:** PR author — per CI_CONTRACT.md
- **Next step:** Reproduce locally with: mvn -pl api-tests,ui-tests test -Dgroups=smoke -Dtier=smoke — then fix the change in this PR.
- **Runbook:** [docs/runbooks/smoke-failure.md](https://github.com/SamikshaPatel/ci-quality-gates/blob/aa29646c9d1a9fa5bbde6e562484bd142482cd91/docs/runbooks/smoke-failure.md)

| Test | Status | Class | First failure |
|---|---|---|---|
| `DemoFaultTests.SmokeCodeFault.pingContractChanged` | FAILED | CODE | Ping contract changed: unexpected status code ==> expected: <200> but was: <201> |

**Evidence:** [Run and artifacts (Allure results, Playwright traces, Surefire XML)](https://github.com/SamikshaPatel/ci-quality-gates/actions/runs/41878065349) · [CI contract](https://github.com/SamikshaPatel/ci-quality-gates/blob/aa29646c9d1a9fa5bbde6e562484bd142482cd91/CI_CONTRACT.md) · [Gate config](https://github.com/SamikshaPatel/ci-quality-gates/blob/aa29646c9d1a9fa5bbde6e562484bd142482cd91/quality-gates.yml) · [Run history](https://samikshapatel.github.io/ci-quality-gates/)
