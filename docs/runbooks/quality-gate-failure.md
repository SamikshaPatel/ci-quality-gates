# Runbook: non-critical quality gate

The run report's reason line says which of three cases happened. For scripts, `gate-results/quality-gate-failure.txt` carries the same facts.

## Pass rate below threshold — owner: PR author
Real failures pushed the tier below its pass-rate threshold. Hard failures are never quarantined.

1. Fix the failing tests, or the change that broke them.
2. Reproduce locally with `mvn -pl api-tests,ui-tests test -Dgroups=regression -Dtier=regression -Dsurefire.rerunFailingTestsCount=1`.

## Flake rate over the limit — owner: QA, with each test's author
This case is handled automatically, and no one needs to act to unblock the PR:

1. The flaky tests are added to `quarantine.json`. On a PR, the bot commits that change to the branch.
2. A Jira ticket is opened for each test, and the ticket links back to the run.
3. The tier reruns without them. If the rerun passes, the PR can merge.

Follow-up for QA: fix each test through its ticket. The nightly run releases a test after 7 consecutive clean runs. A test still quarantined after 14 days fails the nightly run.

## Quarantine cap reached — owner: QA lead
Quarantining these tests would take quarantine above its cap of 5, so the evaluator refused and the PR is blocked.

1. Look at `quarantine.json`: which tests have been quarantined longest, and what do their tickets say?
2. Fix or delete quarantined tests until there is room. Do **not** raise the cap to get a PR through. Changing the cap is a reviewed change to `quality-gates.yml` with a reason.
3. Rerun the workflow.
