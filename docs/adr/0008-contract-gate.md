# ADR-0008: Contract tests are their own gate, between smoke and critical

**Status:** Accepted · 2026-11-05

## Context
Behaviour tests check values: "the booking I created has this name". When a field is renamed or changes type, they fail with a confusing message, or not at all if no test reads that field. In a system where other modules consume an API, such as invoicing and reporting reading booking data, a shape change breaks the consumers silently.

## Decision
- Four contract tests in `BookingContractTest` validate every consumed response against a JSON schema in `api-tests/src/test/resources/schemas/`.
- The schemas hold what consumers rely on: required fields and their types. **Extra fields are allowed**, so the provider can add fields freely; removing, renaming or retyping one fails the gate.
- Contract runs as its own gate after smoke and before critical: 100% pass, no retries, because a schema mismatch is never flaky.
- A failure is owned by the PR author as the API provider, and the runbook prescribes expand, then contract for intended changes.

## Consequences
- A shape change fails one fast gate with a message naming the field, before slower behaviour tests run.
- Schemas are code: changing one is a reviewed PR, which makes every contract change visible.
- These are provider-side schema checks. Teams with independent services usually go further with consumer-driven contracts (Pact), where each consumer publishes what it needs; the gate's position and rule stay the same.
