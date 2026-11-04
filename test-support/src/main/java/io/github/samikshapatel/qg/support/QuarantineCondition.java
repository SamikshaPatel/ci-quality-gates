package io.github.samikshapatel.qg.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * Skips any test listed in quarantine.json, unless -Dinclude.quarantined=true
 * (the nightly run sets it, so quarantined tests keep building their green streak).
 * Registered globally through the JUnit ServiceLoader, so no test has to opt in.
 */
public class QuarantineCondition implements ExecutionCondition {

    private static volatile Set<String> quarantined;

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        if (context.getTestMethod().isEmpty()) {
            return ConditionEvaluationResult.enabled("class level");
        }
        if (Boolean.getBoolean("include.quarantined")) {
            return ConditionEvaluationResult.enabled("quarantined tests included in this run");
        }
        String id = context.getRequiredTestClass().getName() + "#" + context.getRequiredTestMethod().getName();
        if (load().contains(id)) {
            return ConditionEvaluationResult.disabled("Quarantined: " + id + " (see quarantine.json)");
        }
        return ConditionEvaluationResult.enabled("not quarantined");
    }

    private static Set<String> load() {
        if (quarantined == null) {
            Set<String> ids = new HashSet<>();
            String file = System.getProperty("quarantine.file", "quarantine.json");
            Path path = Path.of(file);
            if (Files.exists(path)) {
                try {
                    JsonNode root = new ObjectMapper().readTree(path.toFile());
                    for (JsonNode t : root.path("tests")) {
                        ids.add(t.path("id").asText());
                    }
                } catch (IOException e) {
                    throw new IllegalStateException("quarantine.json is unreadable: " + path, e);
                }
            }
            quarantined = ids;
        }
        return quarantined;
    }
}
