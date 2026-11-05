#!/usr/bin/env bash
# Stability check (ADR-0010): a new or changed test class must pass STABILITY_RUNS runs in a row,
# with no retries, before it can merge. A flaky test is cheapest to catch in the PR that adds it.
#
# Reads STABILITY_TESTS ("module|fully.qualified.Class;...") and STABILITY_RUNS from the test plan.
# Writes gate-results/stability.md for the run report, and gate-results/stability-failed on failure.
set -uo pipefail

runs="${STABILITY_RUNS:-3}"
tests="${STABILITY_TESTS:-}"
summary="gate-results/stability.md"
mkdir -p gate-results
rm -f "$summary" gate-results/stability-failed

if [[ -z "$tests" ]]; then
  echo "No new or changed test classes: nothing to check."
  exit 0
fi

declare -A by_module
IFS=';' read -ra items <<< "$tests"
for item in "${items[@]}"; do
  module="${item%%|*}"
  class="${item##*|}"
  by_module[$module]="${by_module[$module]:+${by_module[$module]},}${class##*.}"
done

echo "### Stability check: new or changed tests, ${runs} runs each, no retries" > "$summary"
echo >> "$summary"
status=0
for module in "${!by_module[@]}"; do
  classes="${by_module[$module]}"
  passed_all=true
  for (( i = 1; i <= runs; i++ )); do
    echo "::group::Stability: ${classes} in ${module} (run ${i} of ${runs})"
    mvn -B -ntp -q -pl "$module" test -Dtest="$classes" -Dtier="stability-${i}" \
      -Dsurefire.failIfNoSpecifiedTests=false -Dsurefire.rerunFailingTestsCount=0
    code=$?
    echo "::endgroup::"
    if [[ $code -ne 0 ]]; then
      echo "- ❌ \`${classes}\` (${module}) failed on run ${i} of ${runs}. Owner: PR author. A new or changed test must pass every run before merge." >> "$summary"
      echo "::error::Stability check failed: ${classes} (${module}) failed on run ${i} of ${runs}."
      passed_all=false
      status=1
      break
    fi
  done
  if [[ "$passed_all" == "true" ]]; then
    echo "- ✅ \`${classes}\` (${module}) passed ${runs} of ${runs} runs" >> "$summary"
  fi
done

if [[ $status -ne 0 ]]; then
  touch gate-results/stability-failed
fi
cat "$summary"
exit $status
