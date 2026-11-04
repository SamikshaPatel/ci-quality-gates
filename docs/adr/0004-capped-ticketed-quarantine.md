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
- The bot commits to PR branches and to `main`. That is acceptable for a single file the bot alone writes.
