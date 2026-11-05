# ADR-0015: A Grafana dashboard that reads the run history, with no new storage

**Status:** Accepted · 2026-11-05

## Context
The run-history site answers "what happened in this run". Managers and on-call engineers ask a different question: "how healthy is the pipeline this month?" They already look at Grafana for production metrics, so quality signals belong next to them, not on yet another site. The usual way in, pushing metrics to Prometheus or a database, adds a service to run, secure and pay for.

## Decision
- The dashboard reads the run history's published JSON (`runs.json`, `flakiness.json`) over HTTPS with the **Infinity** data source. GitHub Pages stays the single store; Grafana only displays it.
- Nine panels: latest verdict, green rate over the last 20 runs, quarantine size against the cap, tests at or over the flakiness limit, the non-critical pass and flake rate against their thresholds, p95 against the performance limit, PR duration against the 10-minute budget, the flakiest tests, and recent runs with links. Demo scenarios are excluded, as on the site.
- The dashboard is **generated from code** (`grafana/scripts/build_dashboard.py`): every query is a reviewable JSONata expression, and CI fails if the committed JSON drifts from the generator.
- A CI check (`grafana.yml`) starts a pinned Grafana with the dashboard provisioned and runs **every panel's query** through Grafana's query API. An import that loads but shows red panels is caught before merge. A screenshot is kept as evidence.
- No template variables, so the dashboard can be shared as a public link from Grafana Cloud.

## Consequences
- Grafana Cloud's free tier is enough; there is no exporter or database to maintain.
- The data is as fresh as the last GitHub Pages deploy, usually a minute or two behind a run. Fine for trends, not for alerting on a single run; the PR comment and job status do that.
- Larger teams would push the same fields to Prometheus or a time-series store and alert on them. The panels and thresholds carry over unchanged; only the data source changes.
