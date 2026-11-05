#!/usr/bin/env python3
"""Runs every panel's query through a live Grafana and fails if any query errors.

A dashboard can import fine and still show "No data" or a red error on every panel, so CI checks
the queries themselves: each panel's target goes to Grafana's /api/ds/query, exactly as the
browser would send it, and the response must have no error and the expected columns.

    python3 grafana/scripts/verify_dashboard.py http://localhost:3000
"""
import base64
import json
import os
import pathlib
import sys
import urllib.request

grafana = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:3000"
password = os.environ.get("GRAFANA_ADMIN_PASSWORD", "local-only")
auth = "Basic " + base64.b64encode(f"admin:{password}".encode()).decode()
board = json.loads((pathlib.Path(__file__).resolve().parents[1] / "dashboards" / "quality-gates.json").read_text())

failures = 0
for panel in board["panels"]:
    target = panel["targets"][0]
    body = json.dumps({"from": "now-90d", "to": "now", "queries": [target]}).encode()
    req = urllib.request.Request(f"{grafana}/api/ds/query", data=body, method="POST",
                                 headers={"Content-Type": "application/json", "Authorization": auth})
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            result = json.load(resp)["results"]["A"]
    except urllib.error.HTTPError as e:
        result = {"error": f"HTTP {e.code}: {e.read().decode()[:300]}"}
    frames = result.get("frames") or []
    names = [f["name"] for fr in frames for f in fr["schema"]["fields"]]
    rows = len(frames[0]["data"]["values"][0]) if frames and frames[0]["data"]["values"] else 0
    expected = [c["text"] for c in target["columns"]]
    missing = [n for n in expected if n not in names] if rows else []
    first = {f["name"]: fr["data"]["values"][i][0] for fr in frames[:1]
             for i, f in enumerate(fr["schema"]["fields"]) if rows}
    if rows and all(v is None for v in first.values()):
        result["error"] = "every value is null: the query matched nothing in the data"
    if result.get("error") or missing:
        failures += 1
        print(f"FAIL  {panel['title']}: {result.get('error') or 'missing columns ' + str(missing)}")
    else:
        print(f"ok    {panel['title']}: {rows} row(s) {json.dumps(first)[:160]}")

print(f"{len(board['panels']) - failures} of {len(board['panels'])} panel queries succeeded")
sys.exit(1 if failures else 0)
