package io.github.samikshapatel.qg.api;

import io.github.samikshapatel.qg.support.Tier;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;

/**
 * Critical path: the booking lifecycle a release can never break.
 * Gated at 100% — a pass on retry still fails this gate, and these tests are never quarantined.
 */
@Tag(Tier.CRITICAL)
class BookingCriticalTest extends BaseApiTest {

    @Test
    void createBookingReturnsId() {
        String first = uniqueName("Ada");
        given().spec(spec).body(booking(first, "Lovelace"))
                .when().post("/booking")
                .then().statusCode(200)
                .body("bookingid", greaterThan(0))
                .body("booking.firstname", equalTo(first));
    }

    @Test
    void getBookingReturnsCreatedData() {
        String first = uniqueName("Grace");
        int id = createBooking(booking(first, "Hopper"));
        given().spec(spec).when().get("/booking/" + id)
                .then().statusCode(200)
                .body("firstname", equalTo(first))
                .body("lastname", equalTo("Hopper"))
                .body("totalprice", equalTo(245))
                .body("bookingdates.checkin", equalTo("2027-03-01"));
    }

    @Test
    void updateBookingWithTokenSucceeds() {
        int id = createBooking(booking(uniqueName("Alan"), "Turing"));
        String updated = uniqueName("Alan-Updated");
        given().spec(spec).cookie("token", token()).body(booking(updated, "Turing"))
                .when().put("/booking/" + id)
                .then().statusCode(200)
                .body("firstname", equalTo(updated));
    }

    @Test
    void deleteBookingWithTokenRemovesIt() {
        int id = createBooking(booking(uniqueName("Katherine"), "Johnson"));
        given().spec(spec).cookie("token", token())
                .when().delete("/booking/" + id)
                .then().statusCode(201);
        given().spec(spec).when().get("/booking/" + id).then().statusCode(404);
    }

    @Test
    void updateWithoutTokenIsRejected() {
        int id = createBooking(booking(uniqueName("Margaret"), "Hamilton"));
        given().spec(spec).body(booking("Intruder", "NoToken"))
                .when().put("/booking/" + id)
                .then().statusCode(403);
    }
}
