#!/usr/bin/env bash
# Builds this run's Allure report (ADR-0012): every test, with steps, request and response
# bodies, retries, failure screenshots and categories, plus trend charts across runs.
#
#   gate-results/allure/index.html    single-file report, published by publish-history.sh
#   gate-results/allure-history/      trend data, carried into the next run's report
#
# Trend history comes from the previous published report on gh-pages, so trends survive
# across runs without a server.
set -uo pipefail

version="${ALLURE_VERSION:-2.46.1}"
results="gate-results/allure-results"
evaluator="gate-evaluator/target/gate-evaluator.jar"
rm -rf "$results" gate-results/allure gate-results/allure-history
mkdir -p "$results"

for module in api-tests ui-tests; do
  if [[ -d "${module}/target/allure-results" ]]; then
    cp -r "${module}/target/allure-results/." "$results/"
  fi
done
if [[ -z "$(ls -A "$results")" ]]; then
  echo "No Allure results in this run, so there is no Allure report."
  exit 0
fi

# Trend history from the last published report.
if git fetch -q --depth 1 origin gh-pages 2>/dev/null \
   && git cat-file -e FETCH_HEAD:allure-history 2>/dev/null; then
  mkdir -p "$results/history"
  git archive FETCH_HEAD allure-history | tar -x -C "$results/history" --strip-components=1
fi

# Categories (ENV, CODE, flaky) come from the same patterns the gates use.
java -jar "$evaluator" allure-categories --file "$results/categories.json"

run_url="${GITHUB_SERVER_URL:-}/${GITHUB_REPOSITORY:-}/actions/runs/${GITHUB_RUN_ID:-}"
cat > "$results/environment.properties" <<EOF
Commit=${GITHUB_SHA:-local}
Branch=${GITHUB_HEAD_REF:-${GITHUB_REF_NAME:-local}}
Trigger=${GITHUB_EVENT_NAME:-local}
Scope=${TEST_SCOPE:-full}
Demo.scenario=${DEMO_SCENARIO:-none}
Promotion.stage=${DEPLOY_STAGE:-none}
API.under.test=${API_BASE_URL:-http://localhost:3001 (Restful-Booker in Docker)}
UI.under.test=${UI_BASE_URL:-https://www.saucedemo.com}
EOF
jq -n --arg url "$run_url" --arg run "${GITHUB_RUN_NUMBER:-0}" \
  '{name: "GitHub Actions", type: "github", url: $url, buildOrder: ($run | tonumber), buildName: ("Run " + $run), buildUrl: $url}' \
  > "$results/executor.json"

# Allure command line, from its official GitHub release; cached by the workflow.
cli_dir="${HOME}/.cache/allure/allure-${version}"
if [[ ! -x "${cli_dir}/bin/allure" ]]; then
  mkdir -p "${HOME}/.cache/allure"
  curl -sSfL -o /tmp/allure.zip \
    "https://github.com/allure-framework/allure2/releases/download/${version}/allure-${version}.zip"
  unzip -q -o /tmp/allure.zip -d "${HOME}/.cache/allure"
fi
allure="${cli_dir}/bin/allure"

# A full report gives the trend history for the next run; the single-file one is what gets published.
"$allure" generate "$results" -o gate-results/allure-full --clean > /dev/null
cp -r gate-results/allure-full/history gate-results/allure-history
"$allure" generate "$results" -o gate-results/allure --clean --single-file > /dev/null
rm -rf gate-results/allure-full
echo "[gate] Allure report: $(du -sh gate-results/allure/index.html | cut -f1), $(ls "$results"/*-result.json | wc -l) test results"
