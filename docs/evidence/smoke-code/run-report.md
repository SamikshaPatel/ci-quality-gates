## Run report

commit `d834851` · 2026-11-04 16:36 UTC · trigger: workflow_dispatch · demo scenario: `smoke-code`

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
- **Runbook:** [docs/runbooks/smoke-failure.md](https://github.com/SamikshaPatel/ci-quality-gates/blob/d834851547cdc7f67858a6f394a32aa0cc80e23f/docs/runbooks/smoke-failure.md)

| Test | Status | Class | First failure |
|---|---|---|---|
| `DemoFaultTests.SmokeCodeFault.pingContractChanged` | FAILED | CODE | Ping contract changed: unexpected status code ==> expected: <200> but was: <201> |

**Evidence:** [Run and artifacts (Allure results, Playwright traces, Surefire XML)](https://github.com/SamikshaPatel/ci-quality-gates/actions/runs/41868697802) · [CI contract](https://github.com/SamikshaPatel/ci-quality-gates/blob/d834851547cdc7f67858a6f394a32aa0cc80e23f/CI_CONTRACT.md) · [Gate config](https://github.com/SamikshaPatel/ci-quality-gates/blob/d834851547cdc7f67858a6f394a32aa0cc80e23f/quality-gates.yml)
