#!/usr/bin/env bash
# Runs one tier's tests and lets the gate evaluator judge them.
# Used identically by GitHub Actions and Jenkins, so the contract does not depend on the CI tool.
#
#   scripts/run-gate.sh <smoke|critical|regression> [attempt]
#
# Exit codes come from the evaluator: 0 pass, 1 fail, 3 flaky tests quarantined (rerun the tier).
# Maven never decides the verdict (-Dmaven.test.failure.ignore): the gate does.
set -uo pipefail

tier="$1"
attempt="${2:-1}"
evaluator="gate-evaluator/target/gate-evaluator.jar"
retries="$(java -jar "$evaluator" retries --tier "$tier")"

extra=()
if [[ -n "${DEMO_SCENARIO:-}" && "${DEMO_SCENARIO}" != "none" ]]; then
  # Deliberate fault tests are excluded by default; a demo scenario switches them on (ADR-0005).
  extra+=("-DexcludedGroups=no-exclusions" "-Ddemo.scenario=${DEMO_SCENARIO}")
fi
if [[ "${INCLUDE_QUARANTINED:-false}" == "true" ]]; then
  extra+=("-Dinclude.quarantined=true")
fi

for module in api-tests ui-tests; do
  dir="${module}/target/reports/${tier}"
  if [[ "${attempt}" -gt 1 && -d "${dir}" ]]; then
    # Keep the earlier attempt's reports as evidence of what was quarantined and why.
    rm -rf "${dir}-attempt$(( attempt - 1 ))"
    mv "${dir}" "${dir}-attempt$(( attempt - 1 ))"
  else
    rm -rf "${dir}"
  fi
done

echo "::group::${tier} tests (attempt ${attempt}, retries ${retries})"
start=$(date +%s)
mvn -B -ntp -pl api-tests,ui-tests test \
  -Dtier="${tier}" -Dgroups="${tier}" \
  -Dsurefire.rerunFailingTestsCount="${retries}" \
  -Dmaven.test.failure.ignore=true \
  "${extra[@]}"
mvn_code=$?
echo "::endgroup::"
echo "[gate] ${tier} tests took $(( $(date +%s) - start ))s"
if [[ $mvn_code -ne 0 ]]; then
  echo "::warning::Maven exited ${mvn_code} — a build problem, not a test verdict. The gate judges whatever ran."
fi

java -jar "$evaluator" evaluate --tier "${tier}" --attempt "${attempt}" \
  --reports "api-tests/target/reports/${tier},ui-tests/target/reports/${tier}"
