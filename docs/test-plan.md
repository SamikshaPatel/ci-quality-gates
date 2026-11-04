# Test plan

25 tests, written first and tagged from the plan. They cover two systems: 13 API tests on Restful-Booker and 12 UI tests on SauceDemo. A test is **critical** if a release that breaks it could not ship.

| Tier | API | UI | Total | Gate rule |
|---|---|---|---|---|
| Smoke | 3 | 2 | 5 | 100%, no retries |
| Critical | 5 | 4 | 9 | 100%, no retry credit |
| Regression (non-critical) | 5 | 6 | 11 | ≥ 80% pass, ≤ 20% flaky |
| **Total** | **13** | **12** | **25** | |

## API — Restful-Booker (`api-tests`)

| # | Test | Tier | Checks |
|---|---|---|---|
| A1 | `ApiSmokeTest.healthCheckReturns201` | Smoke | `GET /ping` answers 201 |
| A2 | `ApiSmokeTest.authReturnsToken` | Smoke | `POST /auth` returns a token |
| A3 | `ApiSmokeTest.bookingListReturnsIds` | Smoke | `GET /booking` returns ids |
| A4 | `BookingCriticalTest.createBookingReturnsId` | Critical | Create returns an id and the saved data |
| A5 | `BookingCriticalTest.getBookingReturnsCreatedData` | Critical | Read returns exactly what was created |
| A6 | `BookingCriticalTest.updateBookingWithTokenSucceeds` | Critical | Authorised full update |
| A7 | `BookingCriticalTest.deleteBookingWithTokenRemovesIt` | Critical | Authorised delete, then 404 |
| A8 | `BookingCriticalTest.updateWithoutTokenIsRejected` | Critical | Security: no token, 403 |
| A9 | `BookingRegressionTest.partialUpdateChangesOnlyGivenFields` | Regression | PATCH changes one field only |
| A10 | `BookingRegressionTest.filterByFirstnameFindsBooking` | Regression | Search by first name |
| A11 | `BookingRegressionTest.filterByCheckinExcludesEarlierBookings` | Regression | Date filter |
| A12 | `BookingRegressionTest.createWithMissingFieldsIsRejected` | Regression | Invalid payload rejected |
| A13 | `BookingRegressionTest.getUnknownBookingReturns404` | Regression | Unknown id, 404 |

## UI — SauceDemo (`ui-tests`)

| # | Test | Tier | Checks |
|---|---|---|---|
| U1 | `UiSmokeTest.loginPageLoads` | Smoke | Site reachable, login form visible |
| U2 | `UiSmokeTest.standardUserReachesInventory` | Smoke | Login lands on Products |
| U3 | `CheckoutCriticalTest.addToCartUpdatesBadge` | Critical | Cart badge shows 1 |
| U4 | `CheckoutCriticalTest.completeCheckoutShowsConfirmation` | Critical | End-to-end purchase |
| U5 | `CheckoutCriticalTest.lockedOutUserSeesError` | Critical | Access control message |
| U6 | `CheckoutCriticalTest.logoutReturnsToLogin` | Critical | Session ends |
| U7 | `StoreRegressionTest.sortByPriceLowToHigh` | Regression | Sort order |
| U8 | `StoreRegressionTest.sortByNameZtoA` | Regression | Sort order |
| U9 | `StoreRegressionTest.removeFromCartClearsBadge` | Regression | Cart edit |
| U10 | `StoreRegressionTest.cartKeepsItemsAcrossPages` | Regression | Cart persistence |
| U11 | `StoreRegressionTest.checkoutRequiresPostalCode` | Regression | Form validation |
| U12 | `StoreRegressionTest.productDetailShowsPrice` | Regression | Product detail page |

## Design rules for these tests

- **Independent:** every API test creates its own data with a unique name. No test depends on another or on seeded records.
- **Fast failure on outages:** API calls time out after 5–10 seconds, so an unreachable service fails fast and is classified ENV.
- **Replayable:** every UI test saves a Playwright trace to `ui-tests/target/traces/`.
- **Secrets:** credentials come from environment variables (GitHub secrets). The public demo defaults are used only when a secret is not set.

## Deliberate fault tests (outside the 25)

Eight fault tests in `api-tests/.../faults/DemoFaultTests.java` prove each failure path on demand. They are tagged `demo-fault`, excluded from every normal run, and enabled by a demo scenario ([ADR-0005](adr/0005-deliberate-fault-tests.md)).

| Scenario | What it switches on | Expected result |
|---|---|---|
| `env-down` | Stops the Restful-Booker container (no fault test) | Smoke fails, ENV, owner DevOps |
| `smoke-code` | 1 smoke test with a wrong expectation | Smoke fails, CODE, owner PR author |
| `critical-flake` | 1 critical test that fails once, then passes | Critical fails: no retry credit |
| `flake-breach` | 3 regression tests that fail once, then pass | Flake rate 21.4%, so 3 tests are quarantined with tickets, then the rerun passes |
| `quarantine-cap` | 6 regression tests that fail once, then pass | Quarantine would reach 6 against a cap of 5, so it is refused and the PR blocked |
