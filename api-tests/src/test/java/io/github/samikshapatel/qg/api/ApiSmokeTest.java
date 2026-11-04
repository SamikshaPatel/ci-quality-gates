package io.github.samikshapatel.qg.api;

import io.github.samikshapatel.qg.support.Config;
import io.github.samikshapatel.qg.support.Tier;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

/** API smoke: is the service up, can we authenticate, can we read? Hard gate, no retries. */
@Tag(Tier.SMOKE)
class ApiSmokeTest extends BaseApiTest {

    @Test
    void healthCheckReturns201() {
        given().spec(spec).when().get("/ping").then().statusCode(201);
    }

    @Test
    void authReturnsToken() {
        given().spec(spec)
                .body(Map.of("username", Config.apiUsername(), "password", Config.apiPassword()))
                .when().post("/auth")
                .then().statusCode(200)
                .body("$", not(hasKey("reason")))
                .body("token", notNullValue());
    }

    @Test
    void bookingListReturnsIds() {
        given().spec(spec).when().get("/booking")
                .then().statusCode(200)
                .body("[0].bookingid", notNullValue());
    }
}
