# Runbook: smoke gate failed

**The PR is blocked and later gates were skipped.** Start with the run report's **Failure classification**.

## ENV — owner: DevOps
The environment broke: timeout, refused connection, 5xx or unreachable host.

1. Open the run's **evidence** artifact and read `restful-booker.log`.
2. If the API container is unhealthy, check that `docker compose -f docker-compose.ci.yml up -d --build --wait` succeeded in the setup step. The usual causes are a broken image build or the runner network.
3. If the UI tests failed, check whether `https://www.saucedemo.com` loads.
4. Once the environment is healthy, **rerun the workflow**. Do not change tests to work around an outage.
5. If the same ENV failure recurs on 3 runs in a day, raise it as an infrastructure incident.

## CODE — owner: PR author
A smoke assertion failed against a healthy environment, which means the change broke something basic.

1. Reproduce locally:
   ```bash
   docker compose -f docker-compose.ci.yml up -d --build --wait
   mvn -B install -pl test-support,gate-evaluator -am
   mvn -pl api-tests,ui-tests test -Dgroups=smoke -Dtier=smoke
   ```
2. For a UI failure, open the trace with `npx playwright show-trace ui-tests/target/traces/<test>.zip`.
3. Fix the change in the PR. Smoke tests are never quarantined.

## MIXED
DevOps clears the environment first. The CODE failures may vanish once it is healthy; any that remain go to the PR author.
