#!/usr/bin/env bash
# The non-critical gate, including the quarantine loop:
#   attempt 1 → if the evaluator quarantines flaky tests (exit 3), rerun once without them.
# Leaves gate-results/quarantine-changed when quarantine.json was modified, so the CI job
# can commit it to the PR branch where reviewers see it.
set -uo pipefail

scripts/run-gate.sh regression 1
code=$?

if [[ $code -eq 3 ]]; then
  touch gate-results/quarantine-changed
  echo "::notice::Flaky tests quarantined — rerunning the non-critical tier without them."
  scripts/run-gate.sh regression 2
  code=$?
fi

exit $code
