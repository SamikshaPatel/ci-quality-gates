package io.github.samikshapatel.qg.api;

import io.github.samikshapatel.qg.support.Tier;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

/** Non-critical regression: edge cases and secondary features of the booking API. Eligible for quarantine. */
@Tag(Tier.REGRESSION)
class BookingRegressionTest extends BaseApiTest {

    @Test
    void partialUpdateChangesOnlyGivenFields() {
        String first = uniqueName("Hedy");
        int id = createBooking(booking(first, "Lamarr"));
        given().spec(spec).cookie("token", token()).body(Map.of("totalprice", 999))
                .when().patch("/booking/" + id)
                .then().statusCode(200)
                .body("totalprice", equalTo(999))
                .body("firstname", equalTo(first));
    }

    @Test
    void filterByFirstnameFindsBooking() {
        String first = uniqueName("Radia");
        int id = createBooking(booking(first, "Perlman"));
        given().spec(spec).queryParam("firstname", first)
                .when().get("/booking")
                .then().statusCode(200)
                .body("bookingid", hasItem(id));
    }

    @Test
    void filterByCheckinExcludesEarlierBookings() {
        Map<String, Object> early = new HashMap<>(booking(uniqueName("Early"), "Bird"));
        early.put("bookingdates", Map.of("checkin", "2020-01-01", "checkout", "2020-01-03"));
        int earlyId = createBooking(early);
        List<Integer> ids = given().spec(spec).queryParam("checkin", "2026-12-31")
                .when().get("/booking")
                .then().statusCode(200)
                .extract().path("bookingid");
        org.hamcrest.MatcherAssert.assertThat(ids, not(hasItem(earlyId)));
    }

    @Test
    void createWithMissingFieldsIsRejected() {
        // Restful-Booker answers invalid payloads with 500 — documented behaviour, asserted as-is.
        given().spec(spec).body(Map.of("firstname", "OnlyAName"))
                .when().post("/booking")
                .then().statusCode(500);
    }

    @Test
    void getUnknownBookingReturns404() {
        given().spec(spec).when().get("/booking/999999999").then().statusCode(404);
    }
}
