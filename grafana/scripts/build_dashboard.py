#!/usr/bin/env python3
"""Builds grafana/dashboards/quality-gates.json, the Grafana dashboard over the run-history site.

The dashboard reads runs.json and flakiness.json straight from GitHub Pages through the Infinity
data source, so no database or exporter is needed. Every panel is a JSONata query; keeping them
here, as code, makes them reviewable. Run it after changing a panel:

    python3 grafana/scripts/build_dashboard.py
"""
import json
import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[1]
OUT = ROOT / "dashboards" / "quality-gates.json"            # provisioned: local Grafana and CI
CLOUD = ROOT / "import" / "quality-gates.grafana-cloud.json"  # for Dashboards > Import in Grafana Cloud
SITE = "https://samikshapatel.github.io/ci-quality-gates"
# No template variables: Grafana's public (shared) dashboards do not support them.
PROVISIONED_UID = "infinity-qg"
DS = {"type": "yesoreyeram-infinity-datasource", "uid": PROVISIONED_UID}

# Real runs only: demo scenarios fail on purpose and would skew every trend.
REAL = "$filter($, function($v){ $v.scenario = 'none' })"


def newest(n):
    """The n newest real runs. runs.json is newest first. Index filtering, because Grafana's
    JSONata engine ignores range predicates such as [[0..19]]."""
    return f"$filter({REAL}, function($v, $i){{ $i < {n} }})"


BY_DATE = "function($a, $b){ $a.date > $b.date }"
# Second precision: some parsers reject the nanosecond fractions Java writes.
DATE = "$substring(date, 0, 19) & 'Z'"

QUERIES = {
    "latest": f"( $r := {newest(1)}; {{ 'verdict': $r.verdict, 'branch': $r.branch, 'trigger': $r.trigger }} )",
    "green": f"( $r := {newest(20)}; {{ 'green': $round(100 * $count($r[verdict = 'PASSED']) / $count($r), 1), 'runs': $count($r) }} )",
    "quarantine": f"( $r := {newest(1)}; {{ 'size': $r.quarantineSize }} )",
    # Bind the document to a variable: Grafana's engine resolves bare paths on an object root to null.
    "scored": "( $d := $; { 'runs': $d.eligibleRuns, 'over': $count($d.tests[score >= $d.quarantineScore]) } )",
    "noncritical": (
        f"$sort({REAL}[gates.regression.executed > 0].{{ 'date': " + DATE + ", 'pass': gates.regression.passRate,"
        " 'flake': gates.regression.flakeRate, 'passMin': gates.regression.passRateThreshold,"
        f" 'flakeMax': gates.regression.maxFlakeRate }}, {BY_DATE})"
    ),
    "perf": (
        f"$sort({REAL}[$exists(performance.p95Ms)].{{ 'date': " + DATE + ", 'p95': performance.p95Ms,"
        f" 'p99': performance.p99Ms, 'limit': performance.p95LimitMs }}, {BY_DATE})"
    ),
    "duration": (
        f"$sort({REAL}[trigger = 'pull_request'].{{ 'date': " + DATE + ", 'minutes': $round(durationSec / 60, 1),"
        f" 'budget': 10 }}, {BY_DATE})"
    ),
    "flaky": "( $d := $; $t := $d.tests[failed + flaky > 0]; $t ? $sort($t, function($a, $b){ $a.score < $b.score }) : [] )",
    "recent": (
        f"( $r := {newest(15)}; $r.{{ 'date': " + DATE + ", 'trigger': trigger, 'branch': branch, 'scope': scope,"
        " 'verdict': verdict, 'reason': reason, 'minutes': $round(durationSec / 60, 1), 'run': runUrl } )"
    ),
}


def or_empty(expr):
    """An empty series is "no data yet", not an error: fresh forks have no performance runs."""
    return f"( $x := {expr}; $exists($x) ? $x : [] )"


for _k in ("noncritical", "perf", "duration", "recent"):
    QUERIES[_k] = or_empty(QUERIES[_k])


def col(selector, kind="number", text=None):
    return {"selector": selector, "text": text or selector, "type": kind}


def target(query, file, columns):
    return {
        "refId": "A", "datasource": DS, "type": "json", "source": "url", "parser": "backend",
        "format": "table", "url": f"{SITE}/{file}", "url_options": {"method": "GET", "data": ""},
        "root_selector": QUERIES[query], "columns": columns,
    }


def panel(pid, kind, title, desc, grid, tgt, field=None, options=None, overrides=None):
    return {
        "id": pid, "type": kind, "title": title, "description": desc, "datasource": DS,
        "gridPos": dict(zip("xywh", grid)), "targets": [tgt],
        "fieldConfig": {"defaults": field or {}, "overrides": overrides or []},
        "options": options or {},
    }


def stat_opts(fields):
    return {"reduceOptions": {"calcs": ["lastNotNull"], "fields": fields, "values": False},
            "colorMode": "background", "graphMode": "none", "textMode": "value", "justifyMode": "center"}


def thresholds(*steps):
    return {"mode": "absolute", "steps": [{"color": c, "value": v} for v, c in steps]}


def line(color, dashed=False):
    props = [{"id": "color", "value": {"mode": "fixed", "fixedColor": color}}]
    if dashed:
        props += [{"id": "custom.lineStyle", "value": {"fill": "dash", "dash": [10, 10]}},
                  {"id": "custom.showPoints", "value": "never"}]
    return props


def override(name, props):
    return {"matcher": {"id": "byName", "options": name}, "properties": props}


TS_DEFAULTS = {"custom": {"drawStyle": "line", "lineWidth": 2, "showPoints": "always", "pointSize": 6,
                          "spanNulls": True, "axisSoftMin": 0}}
TS_OPTS = {"legend": {"displayMode": "list", "placement": "bottom"}, "tooltip": {"mode": "multi"}}

panels = [
    panel(1, "stat", "Latest run", "Verdict of the most recent real run (demo scenarios excluded).",
          (0, 0, 6, 5), target("latest", "runs.json", [col("verdict", "string")]),
          field={"mappings": [{"type": "value", "options": {
              "PASSED": {"color": "green", "text": "PASSED"},
              "FAILED": {"color": "red", "text": "FAILED"}}}],
              "color": {"mode": "thresholds"}, "thresholds": thresholds((None, "text"))},
          options=stat_opts("/^verdict$/")),
    panel(2, "stat", "Green rate, last 20 runs", "Share of the last 20 real runs that passed every gate.",
          (6, 0, 6, 5), target("green", "runs.json", [col("green"), col("runs")]),
          field={"unit": "percent", "decimals": 0, "color": {"mode": "thresholds"},
                 "thresholds": thresholds((None, "red"), (80, "orange"), (95, "green"))},
          options=stat_opts("/^green$/")),
    panel(3, "stat", "Quarantined tests", "Tests in quarantine after the latest real run. The cap is 5.",
          (12, 0, 6, 5), target("quarantine", "runs.json", [col("size")]),
          field={"min": 0, "max": 5, "color": {"mode": "thresholds"},
                 "thresholds": thresholds((None, "green"), (3, "orange"), (5, "red"))},
          options=stat_opts("/^size$/")),
    panel(4, "stat", "Tests at or over the flakiness limit",
          "Tests whose flakiness score over recent main-branch runs is at or above the quarantine score (10%).",
          (18, 0, 6, 5), target("scored", "flakiness.json", [col("over"), col("runs")]),
          field={"color": {"mode": "thresholds"}, "thresholds": thresholds((None, "green"), (1, "red"))},
          options=stat_opts("/^over$/")),
    panel(5, "timeseries", "Non-critical tier: pass and flake rate vs limits",
          "Pass rate must stay at or above its threshold; flake rate at or below its limit. Dashed lines are the limits from quality-gates.yml.",
          (0, 5, 12, 9), target("noncritical", "runs.json", [
              col("date", "timestamp"), col("pass", text="Pass rate"), col("flake", text="Flake rate"),
              col("passMin", text="Pass threshold"), col("flakeMax", text="Flake limit")]),
          field={**TS_DEFAULTS, "unit": "percent", "max": 100}, options=TS_OPTS,
          overrides=[override("Pass rate", line("green")), override("Flake rate", line("orange")),
                     override("Pass threshold", line("green", True)), override("Flake limit", line("orange", True))]),
    panel(6, "timeseries", "Performance: p95 response time vs limit",
          "k6 load test, nightly and on Staging. The gate fails when p95 crosses the dashed limit.",
          (12, 5, 12, 9), target("perf", "runs.json", [
              col("date", "timestamp"), col("p95", text="p95"), col("p99", text="p99"), col("limit", text="p95 limit")]),
          field={**TS_DEFAULTS, "unit": "ms"}, options=TS_OPTS,
          overrides=[override("p95", line("blue")), override("p99", line("purple")), override("p95 limit", line("red", True))]),
    panel(7, "timeseries", "PR pipeline duration vs 10-minute budget",
          "Wall-clock time of each PR run. Runs above the dashed line break the runtime budget in the CI contract.",
          (0, 14, 12, 8), target("duration", "runs.json", [
              col("date", "timestamp"), col("minutes", text="Minutes"), col("budget", text="Budget")]),
          field={**TS_DEFAULTS, "unit": "m", "custom": {**TS_DEFAULTS["custom"], "drawStyle": "bars", "fillOpacity": 60}},
          options=TS_OPTS,
          overrides=[override("Minutes", line("blue")),
                     override("Budget", line("red", True) + [{"id": "custom.drawStyle", "value": "line"}])]),
    panel(8, "table", "Flakiest tests (main branch)",
          "Each test's failures and retry-passes over the recent main-branch window. Score = (failed + flaky) / runs.",
          (12, 14, 12, 8), target("flaky", "flakiness.json", [
              col("id", "string", "Test"), col("tier", "string", "Tier"), col("runs", text="Runs"),
              col("failed", text="Failed"), col("flaky", text="Flaky"), col("score", text="Score"),
              col("lastBad", "string", "Last bad run")]),
          field={"custom": {"align": "auto"}}, options={"showHeader": True},
          overrides=[override("Score", [{"id": "unit", "value": "percent"},
                                        {"id": "custom.cellOptions", "value": {"type": "color-background"}},
                                        {"id": "thresholds", "value": thresholds((None, "green"), (5, "orange"), (10, "red"))}])]),
    panel(9, "table", "Recent runs", "The last 15 real runs. Open a run for its report, Allure results and evidence.",
          (0, 22, 24, 9), target("recent", "runs.json", [
              col("date", "timestamp", "When"), col("trigger", "string", "Trigger"), col("branch", "string", "Branch"),
              col("scope", "string", "Scope"), col("verdict", "string", "Verdict"), col("reason", "string", "Reason"),
              col("minutes", text="Minutes"), col("run", "string", "Run")]),
          field={"custom": {"align": "auto"}}, options={"showHeader": True},
          overrides=[override("Verdict", [{"id": "custom.cellOptions", "value": {"type": "color-text"}},
                                          {"id": "mappings", "value": [{"type": "value", "options": {
                                              "PASSED": {"color": "green"}, "FAILED": {"color": "red"}}}]}]),
                     override("Run", [{"id": "links", "value": [{"title": "Open run", "url": "${__value.raw}", "targetBlank": True}]},
                                      {"id": "mappings", "value": [{"type": "regex", "options": {"pattern": ".*", "result": {"text": "Open"}}}]}])]),
]

dashboard = {
    "uid": "ci-quality-gates", "title": "CI quality gates", "tags": ["ci", "quality-gates", "qa"],
    "description": "Gate results, flakiness, performance and pipeline time from the ci-quality-gates run history.",
    "editable": True, "schemaVersion": 39, "version": 1, "time": {"from": "now-30d", "to": "now"},
    "refresh": "1h", "timezone": "browser", "graphTooltip": 1,
    "links": [{"title": "Run history site", "url": "https://samikshapatel.github.io/ci-quality-gates/",
               "type": "link", "targetBlank": True},
              {"title": "CI contract", "url": "https://github.com/SamikshaPatel/ci-quality-gates/blob/main/CI_CONTRACT.md",
               "type": "link", "targetBlank": True}],
    "templating": {"list": []},
    "panels": panels,
}

provisioned = json.dumps(dashboard, indent=2) + "\n"
OUT.parent.mkdir(parents=True, exist_ok=True)
OUT.write_text(provisioned)

# The import copy asks for the Infinity data source at import time, since its uid differs per stack.
cloud = json.loads(provisioned.replace(f'"uid": "{PROVISIONED_UID}"', '"uid": "${DS_INFINITY}"'))
cloud = {"__inputs": [{"name": "DS_INFINITY", "label": "Infinity", "type": "datasource",
                       "pluginId": "yesoreyeram-infinity-datasource", "pluginName": "Infinity"}],
         "__requires": [{"type": "datasource", "id": "yesoreyeram-infinity-datasource", "name": "Infinity", "version": "3.0.0"}],
         **cloud}
cloud.pop("id", None)
CLOUD.parent.mkdir(parents=True, exist_ok=True)
CLOUD.write_text(json.dumps(cloud, indent=2) + "\n")
print(f"Wrote {OUT.name} and {CLOUD.name} with {len(panels)} panels")
