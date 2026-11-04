#!/usr/bin/env bash
# Publishes this run to the run-history site on the gh-pages branch (GitHub Pages):
#   runs/<yyyy-mm-dd_HHMMSS>_run<id>/   run-report.md, gate results, failure file
#   runs.json                           every run's summary, newest first (rebuilt each publish)
#   index.html, run.html                the site itself, copied from site/ on main
#
# Concurrent runs can race to push. Each attempt starts from the latest gh-pages,
# rebuilds runs.json from all runs/*/history-entry.json, and retries on a rejected push.
set -euo pipefail

entry="gate-results/history-entry.json"
if [[ ! -f "$entry" ]]; then
  echo "No history entry — nothing to publish."
  exit 0
fi
: "${GITHUB_TOKEN:?GITHUB_TOKEN is required to push to gh-pages}"
: "${GITHUB_REPOSITORY:?}"

folder="$(jq -r .folder "$entry")"
remote="https://x-access-token:${GITHUB_TOKEN}@github.com/${GITHUB_REPOSITORY}.git"
work="$(mktemp -d)"

for attempt in 1 2 3 4 5; do
  rm -rf "$work" && mkdir -p "$work"
  if git ls-remote --exit-code --heads "$remote" gh-pages >/dev/null 2>&1; then
    git clone -q --depth 1 --branch gh-pages "$remote" "$work"
  else
    git -C "$work" init -q
    git -C "$work" checkout -q --orphan gh-pages
    git -C "$work" remote add origin "$remote"
  fi

  mkdir -p "$work/runs/$folder"
  cp gate-results/*.json gate-results/run-report.md "$work/runs/$folder/"
  cp gate-results/quality-gate-failure.txt "$work/runs/$folder/" 2>/dev/null || true
  cp site/* "$work/"
  touch "$work/.nojekyll"

  # Newest first. Every run carries its own entry, so the index can always be rebuilt from the folders.
  jq -s 'sort_by(.date) | reverse' "$work"/runs/*/history-entry.json > "$work/runs.json"

  git -C "$work" add -A
  git -C "$work" -c user.name="quality-gate-bot" -c user.email="quality-gate-bot@users.noreply.github.com" \
    commit -q -m "history: ${folder}"
  if git -C "$work" push -q origin gh-pages; then
    echo "Published ${folder} to the run history."
    exit 0
  fi
  echo "Push rejected (another run published first) — retrying (${attempt}/5)."
  sleep $(( attempt * 3 ))
done

echo "::warning::Could not publish to the run history after 5 attempts."
exit 0
