# ADR-0011: Build once, promote the same artifact, approve before production

**Status:** Accepted · 2026-11-05

## Context
Passing gates on a PR proves a change is safe to merge, not that it is safe to release. Releases fail when the thing deployed to production is not the thing that was tested: rebuilt from source, built from a newer commit, or configured differently. Regulated teams also need a record of who approved each production change.

## Decision
Every merge to `main` starts the **Promote** workflow:

| Stage | What happens | Gate to move on |
|---|---|---|
| Build once | One versioned artifact, with its SHA-256 recorded | Build succeeds |
| QA | Deploy, verify the checksum, run the full quality gates against that commit | All gates pass |
| Staging | Deploy, verify the checksum, open a change record in Jira, run the full gates again | All gates pass |
| Production | **Waits for a required reviewer** on the `production` environment, deploys, verifies the checksum, runs a post-deployment smoke check, and records the outcome on the change record | Approval, then smoke passes |

- **Same artifact everywhere:** each environment checks the SHA-256 against the build, so a rebuilt or substituted file fails the deployment.
- **Exact commit:** the gates check out the commit that triggered the promotion, not the latest `main`.
- **One promotion at a time:** a newer merge waits instead of overtaking a release in progress.
- **Approval and audit:** GitHub records who approved each production deployment and when; the Jira change record links the version, the run and the outcome.
- `production` accepts deployments from `main` only.

## Consequences
- The promoted artifact here is the quality-gate toolkit itself, because the repo has no application. The mechanics are what carry over: one artifact, gated stages, an approval and an audit trail.
- In a solo repository the author approves their own deployment. On a team, the environment's "prevent self-review" setting makes sure the approver is someone else, which is the segregation of duties auditors expect.
- Real deployments add rollback: a failed production smoke check would redeploy the previous artifact. Here the post-deployment check fails the run and marks the change record.
