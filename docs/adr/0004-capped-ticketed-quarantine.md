# ADR-0004: Quarantine is capped, ticketed, visible and has an exit rule

**Status:** Accepted · 2026-11-04

## Context
Quarantining flaky tests protects delivery speed. Without limits, though, quarantine turns into a place where failing tests are hidden, and coverage quietly shrinks.

## Decision
- **Only for flakiness:** only non-critical tests that passed on retry can be quarantined. Hard failures fail the gate.
- **Never for protected tests:** smoke, critical, and classes matching `never_quarantine` are never quarantined.
- **Capped:** at most 5 tests. If quarantining would exceed the cap, the evaluator refuses and the PR is blocked. Someone has to fix tests before more can be hidden.
- **Ticketed:** each quarantined test gets a Jira ticket automatically.
- **Visible:** on a PR, the change to `quarantine.json` is committed to the branch, so reviewers see it in the diff.
- **Exit rule:** the nightly run includes quarantined tests. After 7 consecutive clean nights a test is released.
- **Age limit:** after 14 days in quarantine, the nightly run fails and names the test.

## Consequences
- Quarantine stays a short-term holding area with an owner, never a permanent skip list.
- `main` is protected, so the bot never pushes to it. On a PR it commits to the PR's branch; nightly changes arrive as a review PR that passes the same gates and needs a human merge.
- The bot pushes with its own token (`QUALITY_BOT_TOKEN`) rather than GitHub's built-in one, because pushes made with the built-in token deliberately start no new pipeline run, which would leave the PR's newest commit without a green check.
