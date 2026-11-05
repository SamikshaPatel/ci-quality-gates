# Runbook: contract gate failed

**The PR is blocked and later gates were skipped. Owner: PR author (the API provider).** An API response no longer matches the JSON schema its consumers rely on. Behaviour tests would also fail, but less clearly; the contract gate names the field.

1. Read the failure in the run report. The validator's message names the problem, for example `object has missing required properties (["totalprice"])` or `instance type (string) does not match any allowed primitive type (allowed: ["integer"])`.
2. **If the change is a mistake,** fix it in the PR.
3. **If the change is intended,** don't rename, remove or retype a field in one step. Use expand, then contract:
   1. Add the new field next to the old one. Both stay in the response, and the schema still passes.
   2. Consumers move to the new field in their own PRs.
   3. Remove the old field in a later release, and update the schema in `api-tests/src/test/resources/schemas/` in that same PR, with QA reviewing.
4. If the classification is **ENV**, the API was unreachable during the check. That goes to DevOps; follow the [smoke runbook](smoke-failure.md).

Reproduce locally:
```bash
mvn -pl api-tests test -Dgroups=contract -Dtier=contract
```
