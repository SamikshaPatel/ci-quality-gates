#!/usr/bin/env bash
# Performance gate (ADR-0014): runs the k6 load test against the API under test, then lets the
# gate evaluator apply the limits from quality-gates.yml. Exit 0 = within limits, 1 = breach.
#
# k6 runs from its official container image (pinned), on the host network so it reaches the
# API container on localhost.
set -uo pipefail

evaluator="gate-evaluator/target/gate-evaluator.jar"
k6_image="${K6_IMAGE:-grafana/k6:0.54.0}"
mkdir -p gate-results
rm -f gate-results/k6-summary.json gate-results/performance.json

# Load settings (virtual users, duration) come from quality-gates.yml too.
eval "$(java -jar "$evaluator" performance-load | sed 's/^/export /')"

echo "::group::k6: ${VUS} virtual users for ${DURATION} against ${API_BASE_URL:-http://localhost:3001}"
docker run --rm --network host --user "$(id -u):$(id -g)" \
  -v "$PWD:/work" -w /work \
  -e API_BASE_URL="${API_BASE_URL:-http://localhost:3001}" -e VUS -e DURATION \
  -e SUMMARY_FILE=gate-results/k6-summary.json \
  "$k6_image" run --quiet perf/booking-load.js
echo "::endgroup::"

if [[ ! -f gate-results/k6-summary.json ]]; then
  echo "::error::k6 produced no summary, so the performance gate cannot judge this run."
  exit 1
fi
java -jar "$evaluator" performance --summary gate-results/k6-summary.json
