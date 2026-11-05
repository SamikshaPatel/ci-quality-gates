package io.github.samikshapatel.qg.api;

import io.github.samikshapatel.qg.support.Config;
import io.github.samikshapatel.qg.support.Tier;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;

/**
 * Contract gate (ADR-0008): every response the consumers depend on must match its JSON schema.
 * Behaviour tests check values; these check shape, so a renamed, removed or retyped field fails
 * here first, with a message naming the field. 100%, no retries: a schema mismatch is never flaky.
 */
@Tag(Tier.CONTRACT)
class BookingContractTest extends BaseApiTest {

    @Test
    void authTokenMatchesSchema() {
        given().spec(spec)
                .body(Map.of("username", Config.apiUsername(), "password", Config.apiPassword()))
                .when().post("/auth")
                .then().statusCode(200)
                .body(matchesJsonSchemaInClasspath("schemas/auth-token.json"));
    }

    @Test
    void bookingIdsMatchSchema() {
        given().spec(spec).when().get("/booking")
                .then().statusCode(200)
                .body(matchesJsonSchemaInClasspath("schemas/booking-ids.json"));
    }

    @Test
    void createBookingResponseMatchesSchema() {
        given().spec(spec).body(booking(uniqueName("Contract"), "Create"))
                .when().post("/booking")
                .then().statusCode(200)
                .body(matchesJsonSchemaInClasspath("schemas/booking-created.json"));
    }

    @Test
    void getBookingMatchesSchema() {
        int id = createBooking(booking(uniqueName("Contract"), "Read"));
        given().spec(spec).when().get("/booking/" + id)
                .then().statusCode(200)
                .body(matchesJsonSchemaInClasspath("schemas/booking.json"));
    }
}
