package io.github.samikshapatel.qg.gate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Opens a Jira Cloud ticket for each test the gate quarantines.
 * Credentials come only from environment variables (GitHub secrets). If they are missing,
 * or Jira is unreachable, the gate still works: the ticket is skipped and the report says so.
 */
public class JiraClient {

    private final String baseUrl;
    private final String email;
    private final String token;
    private final String projectKey;
    private final String issueType;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    public JiraClient(Map<String, String> env) {
        this.baseUrl = trimSlash(env.getOrDefault("JIRA_BASE_URL", ""));
        this.email = env.getOrDefault("JIRA_EMAIL", "");
        this.token = env.getOrDefault("JIRA_API_TOKEN", "");
        this.projectKey = env.getOrDefault("JIRA_PROJECT_KEY", "");
        this.issueType = env.getOrDefault("JIRA_ISSUE_TYPE", "Task");
    }

    public boolean configured() {
        return !baseUrl.isBlank() && !email.isBlank() && !token.isBlank() && !projectKey.isBlank();
    }

    /** Creates the ticket and returns its key, or null when Jira is not configured or the call fails. */
    public String createQuarantineTicket(TestResult test, String runUrl) {
        if (!configured()) {
            System.err.println("[gate] Jira not configured — skipping ticket for " + test.id());
            return null;
        }
        String summary = "[Quarantine] Flaky test: " + test.method() + " (" + simpleName(test.className()) + ")";
        String text = "Quarantined automatically by the quality gate after passing only on retry.\n"
                + "Test: " + test.id() + "\n"
                + "First failure: " + test.shortMessage() + "\n"
                + "Run: " + runUrl + "\n"
                + "Exit rule: leaves quarantine after consecutive clean nightly runs (quality-gates.yml).";
        Map<String, Object> body = Map.of("fields", Map.of(
                "project", Map.of("key", projectKey),
                "issuetype", Map.of("name", issueType),
                "summary", summary,
                "labels", List.of("quarantine", "flaky-test", "ci-quality-gate"),
                "description", adf(text)));
        try {
            String auth = Base64.getEncoder().encodeToString((email + ":" + token).getBytes(StandardCharsets.UTF_8));
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/rest/api/3/issue"))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Basic " + auth)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                System.err.println("[gate] Jira returned " + response.statusCode() + " for " + test.id()
                        + ": " + abbreviate(response.body()));
                return null;
            }
            JsonNode json = mapper.readTree(response.body());
            return json.path("key").asText(null);
        } catch (Exception e) {
            System.err.println("[gate] Jira call failed for " + test.id() + ": " + e.getMessage());
            return null;
        }
    }

    public String browseUrl(String key) {
        return key == null || baseUrl.isBlank() ? null : baseUrl + "/browse/" + key;
    }

    /** Atlassian Document Format: one paragraph per line. */
    private static Map<String, Object> adf(String text) {
        List<Map<String, Object>> paragraphs = text.lines()
                .map(line -> Map.<String, Object>of("type", "paragraph",
                        "content", List.of(Map.of("type", "text", "text", line.isEmpty() ? " " : line))))
                .toList();
        return Map.of("type", "doc", "version", 1, "content", paragraphs);
    }

    private static String simpleName(String className) {
        return className.substring(className.lastIndexOf('.') + 1);
    }

    private static String trimSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String abbreviate(String s) {
        return s == null ? "" : (s.length() > 300 ? s.substring(0, 300) + "..." : s);
    }
}
