package io.github.samikshapatel.qg.api;

import io.github.samikshapatel.qg.support.Config;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeAll;

import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;

/**
 * Shared setup for Restful-Booker tests. Every test creates its own data with a
 * unique name, so tests never depend on each other or on seeded records.
 */
abstract class BaseApiTest {

    protected static RequestSpecification spec;

    @BeforeAll
    static void configure() {
        // Short timeouts: an unreachable API should fail fast and be classified ENV, not hang the gate.
        RestAssuredConfig config = RestAssured.config().httpClient(HttpClientConfig.httpClientConfig()
                .setParam("http.connection.timeout", 5000)
                .setParam("http.socket.timeout", 10000));
        spec = new RequestSpecBuilder()
                .setBaseUri(Config.apiBaseUrl())
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                .setConfig(config)
                .build();
    }

    protected static String uniqueName(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    protected static Map<String, Object> booking(String firstname, String lastname) {
        return Map.of(
                "firstname", firstname,
                "lastname", lastname,
                "totalprice", 245,
                "depositpaid", true,
                "bookingdates", Map.of("checkin", "2027-03-01", "checkout", "2027-03-05"),
                "additionalneeds", "Breakfast");
    }

    protected static int createBooking(Map<String, Object> body) {
        return given().spec(spec).body(body)
                .when().post("/booking")
                .then().statusCode(200)
                .extract().path("bookingid");
    }

    protected static String token() {
        return given().spec(spec)
                .body(Map.of("username", Config.apiUsername(), "password", Config.apiPassword()))
                .when().post("/auth")
                .then().statusCode(200)
                .extract().path("token");
    }
}
