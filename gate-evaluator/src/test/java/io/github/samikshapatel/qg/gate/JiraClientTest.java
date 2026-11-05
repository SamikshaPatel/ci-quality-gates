package io.github.samikshapatel.qg.gate;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Talks to a fake Jira on localhost, so the real request shape is checked without creating tickets. */
class JiraClientTest {

    private HttpServer server;
    private final AtomicInteger status = new AtomicInteger(201);
    private final AtomicReference<String> body = new AtomicReference<>();
    private final AtomicReference<String> auth = new AtomicReference<>();
    private String baseUrl;

    private static final TestResult FLAKY = new TestResult("io.x.api.BookingRegressionTest#searchFlaky",
            "io.x.api.BookingRegressionTest", "searchFlaky", TestResult.Status.FLAKY,
            "AssertionError", "Simulated flaky failure");

    @BeforeEach
    void startFakeJira() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/rest/api/3/issue", exchange -> {
            auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = (status.get() / 100 == 2 ? "{\"key\":\"QG-7\"}" : "{\"errors\":{}}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status.get(), response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopFakeJira() {
        server.stop(0);
    }

    private JiraClient client() {
        return new JiraClient(Map.of("JIRA_BASE_URL", baseUrl + "/", "JIRA_EMAIL", "qa@example.com",
                "JIRA_API_TOKEN", "secret", "JIRA_PROJECT_KEY", "QG"));
    }

    @Test
    void createsTicketWithLabelsAndBasicAuth() {
        String key = client().createQuarantineTicket(FLAKY, "https://example/run/1");
        assertEquals("QG-7", key);
        String expected = "Basic " + Base64.getEncoder().encodeToString("qa@example.com:secret".getBytes(StandardCharsets.UTF_8));
        assertEquals(expected, auth.get());
        assertTrue(body.get().contains("[Quarantine] Flaky test: searchFlaky (BookingRegressionTest)"), body.get());
        assertTrue(body.get().contains("\"key\":\"QG\""), body.get());
        assertTrue(body.get().contains("quarantine"), body.get());
        assertTrue(body.get().contains("https://example/run/1"), body.get());
    }

    @Test
    void jiraErrorReturnsNullInsteadOfFailingTheGate() {
        status.set(400);
        assertNull(client().createQuarantineTicket(FLAKY, "run"));
    }

    @Test
    void unconfiguredClientSkipsTickets() {
        JiraClient none = new JiraClient(Map.of());
        assertFalse(none.configured());
        assertNull(none.createQuarantineTicket(FLAKY, "run"));
        assertNull(none.browseUrl("QG-1"));
    }

    @Test
    void browseUrlTrimsTrailingSlash() {
        assertEquals(baseUrl + "/browse/QG-7", client().browseUrl("QG-7"));
        assertNull(client().browseUrl(null));
    }
}
