# Runbook: critical gate failed

**The PR is blocked. Owner: PR author, with QA reviewing.** Critical tests cover flows a release cannot break, and they are never quarantined.

1. Read the failing test and its first failure in the run report.
2. **If the status is FLAKY** (the test passed on retry), it still fails this gate. Treat it as a real defect: an intermittent failure in checkout or booking reaches customers. Common causes are race conditions, missing waits or shared state.
3. **If the status is FAILED**, the change broke a critical flow. Reproduce it locally:
   ```bash
   mvn -pl api-tests,ui-tests test -Dgroups=critical -Dtier=critical
   ```
4. Fix the change, or fix the test if QA agrees the test is wrong, and push. The gate reruns on the next commit.
5. **Never** move a test out of the critical tier to get a PR through. Changing a test's tier needs a QA-approved PR to `docs/test-plan.md`.
