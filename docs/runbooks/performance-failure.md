# Runbook: performance gate failed

**The run is marked failed. Owner: the author of the change, with DevOps if the environment is slow.** Under load (10 virtual users for 30 seconds), the booking API's 95th-percentile response time or its error rate went over the limit in `quality-gates.yml`.

1. Read the **Performance** section of the run report: p95, p99, average and error rate against their limits.
2. **Errors over the limit:** open the run's evidence and read `restful-booker.log`. Errors under load usually mean a crash, a connection limit or a timeout.
3. **p95 over the limit, errors fine:** compare with the trend on the run-history site.
   - **A step change** starting with one merge points at that change. Reproduce it locally and profile the slow endpoint.
   - **A one-off spike** on an otherwise flat trend often means a slow CI runner. Rerun once; if the second run is within limits, note it and move on.
4. Never raise a limit to get a run through. Changing a limit is a reviewed change to `quality-gates.yml` with the reason in the PR.

Reproduce locally:
```bash
docker compose -f docker-compose.ci.yml up -d --build --wait
mvn -B install -pl test-support,gate-evaluator -am -DskipTests
mkdir -p gate-results && scripts/run-performance.sh
```
