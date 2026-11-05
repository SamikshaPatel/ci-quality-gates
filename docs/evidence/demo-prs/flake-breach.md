# Demo PR: flake-breach

Switches on 3 non-critical tests that fail once, then pass. Expected: flake rate 21.4% over the 20% limit, so the 3 tests are quarantined with a Jira ticket each; the bot commits quarantine.json to this branch, and the gates rerun on that commit and pass.

This file exists only to give the demo PR a diff. Demo PRs are evidence and are never merged.
