# ADR-0010: A PR runs the tests its changes can affect, and new tests prove they are stable

**Status:** Accepted · 2026-11-05

## Context
Running every test on every PR is simple, but it stops scaling. As suites grow, the PR pipeline passes its time budget, and developers wait on tests their change cannot touch. A documentation fix should not wait for checkout tests. Separately, a flaky test is cheapest to catch in the PR that adds it, before it starts failing other people's builds.

## Decision
- An **impact analysis** step diffs the PR against its base branch and maps each changed file using the `impact` rules in `quality-gates.yml`:

  | Changed files | Gates that run |
  |---|---|
  | Documentation only (`*.md`, `docs/**`, `site/**`) | No test gates. Evaluator tests and security checks still run |
  | API test code only (`api-tests/**`) | Smoke on every module; contract, critical and regression for the API module |
  | UI test code only (`ui-tests/**`) | Smoke on every module; critical and regression for the UI module |
  | Both, or anything else (build files, shared code, gates, pipelines) | The full suite |

- **Smoke always covers every module.** It is cheap, and any change can break a neighbour's basics.
- Unknown files mean the full suite: the rules can only *narrow* a run when they are sure.
- A gate that is not needed is recorded as **NOT SELECTED** with the reason, so the run report says what was skipped and why. It never passes silently.
- Nightly, releases, manual runs and demo scenarios always run the full suite, so nothing goes untested for long.
- **Stability check:** every new or changed test class runs 3 times in a row with no retries. One failure blocks the merge.

## Consequences
- PR feedback stays inside the time budget as the suite grows, and the full nightly run is the safety net.
- Wrong rules are the main risk: a dependency the rules don't know about can skip a test that mattered. That's why unmatched files trigger the full suite, and why the rules are reviewed like any other threshold.
- At larger scale, teams derive impact from the build graph or from coverage data (test impact analysis tools) rather than path rules; the contract (narrow only when sure, full nightly, record what was skipped) stays the same.
