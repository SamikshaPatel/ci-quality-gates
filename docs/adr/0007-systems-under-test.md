# ADR-0007: Self-hosted API, stable public UI site

**Status:** Accepted · 2026-11-04

## Context
Public practice sites are shared with thousands of other users. A gate pointed at one inherits failures it did not cause, which undermines a project about trustworthy gates. The public Restful-Booker instance resets every 10 minutes, which would break CRUD tests mid-run. OrangeHRM's public demo was considered for the UI, but it is shared and slow, and its data changes underneath tests.

## Decision
- **API:** Restful-Booker, built from its public source and run in Docker inside CI (`docker-compose.ci.yml`), with a health check. This is an environment the pipeline controls, and stopping it is a real outage for the `env-down` demo.
- **UI:** SauceDemo. It is a static site built for automation practice, with no shared data, so it is fast and predictable.

## Consequences
- One environment the pipeline controls and one it does not, which is a realistic mix.
- If SauceDemo is down, UI smoke fails as ENV and the run report says so, which is the correct verdict.
