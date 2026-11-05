# Grafana dashboard

A Grafana view of the pipeline's health over time, read straight from the [run history](https://samikshapatel.github.io/ci-quality-gates/) through the Infinity data source. No database, no exporter ([ADR-0015](../docs/adr/0015-grafana-dashboard.md)).

| Panel | Question it answers |
|---|---|
| Latest run · Green rate, last 20 runs | Is the pipeline healthy right now, and has it been? |
| Quarantined tests · Tests at or over the flakiness limit | Is test debt under control, against the cap of 5? |
| Non-critical pass and flake rate vs limits | Is quality drifting toward the threshold before it breaks? |
| p95 response time vs limit | Is performance trending toward the gate? |
| PR pipeline duration vs 10-minute budget | Is the feedback loop still fast? |
| Flakiest tests · Recent runs | What to fix first, and where to click through |

## Grafana Cloud (free tier)

1. **Install the plugin.** In your stack: **Administration → Plugins and data → Plugins**, search **Infinity**, then **Install**.
2. **Add the data source.** **Connections → Data sources → Add new data source → Infinity**. Under **URL, Headers & Params → Allowed hosts**, add `https://samikshapatel.github.io`. Click **Save & test**.
3. **Import the dashboard.** **Dashboards → New → Import**, upload [`import/quality-gates.grafana-cloud.json`](import/quality-gates.grafana-cloud.json), and pick the Infinity data source when asked.
4. **Share it (optional).** On the dashboard: **Share → Share externally**, then **Accept** and copy the public link.

## Locally

```bash
docker compose -f grafana/docker-compose.yml up -d --wait
open http://localhost:3000/d/ci-quality-gates   # anonymous, read-only
```

## Changing a panel

Edit [`scripts/build_dashboard.py`](scripts/build_dashboard.py), then regenerate both JSON files:

```bash
python3 grafana/scripts/build_dashboard.py
```

The **Grafana dashboard** check fails a PR if the JSON drifts from the generator or if any panel's query errors against a real Grafana.
