package io.github.samikshapatel.qg.gate;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Typed view of quality-gates.yml. */
public record GateConfig(
        double smokePassRate,
        double contractPassRate,
        double criticalPassRate,
        boolean criticalFlakyCountsAsFailure,
        double noncriticalPassRate,
        double noncriticalMaxFlakeRate,
        int quarantineMaxTests,
        int quarantineMaxAgeDays,
        int quarantineExitStreak,
        List<String> neverQuarantine,
        List<Pattern> envPatterns,
        Map<String, Owner> owners) {

    public record Owner(String owner, String runbook) {
    }

    public static GateConfig load(Path file) {
        try (Reader reader = Files.newBufferedReader(file)) {
            Map<String, Object> root = new Yaml().load(reader);
            return fromMap(root);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + file, e);
        }
    }

    @SuppressWarnings("unchecked")
    static GateConfig fromMap(Map<String, Object> root) {
        Map<String, Object> smoke = section(root, "smoke");
        Map<String, Object> contract = section(root, "contract");
        Map<String, Object> critical = section(root, "critical");
        Map<String, Object> noncritical = section(root, "noncritical");
        Map<String, Object> quarantine = section(root, "quarantine");
        Map<String, Object> classification = section(root, "classification");
        Map<String, Object> ownersRaw = section(root, "owners");

        List<Pattern> patterns = ((List<String>) classification.getOrDefault("env_patterns", List.of())).stream()
                .map(p -> Pattern.compile(p, Pattern.CASE_INSENSITIVE))
                .toList();
        Map<String, Owner> owners = new java.util.HashMap<>();
        ownersRaw.forEach((key, value) -> {
            Map<String, Object> o = (Map<String, Object>) value;
            owners.put(key, new Owner(String.valueOf(o.get("owner")), String.valueOf(o.get("runbook"))));
        });

        return new GateConfig(
                number(smoke, "pass_rate"),
                number(contract, "pass_rate"),
                number(critical, "pass_rate"),
                Boolean.TRUE.equals(critical.getOrDefault("flaky_counts_as_failure", true)),
                number(noncritical, "pass_rate"),
                number(noncritical, "max_flake_rate"),
                (int) number(quarantine, "max_tests"),
                (int) number(quarantine, "max_age_days"),
                (int) number(quarantine, "exit_green_streak"),
                (List<String>) quarantine.getOrDefault("never_quarantine", List.of()),
                patterns,
                owners);
    }

    public Owner owner(String key) {
        return owners.getOrDefault(key, new Owner("QA", "CI_CONTRACT.md"));
    }

    /** Glob-style match ("*Checkout*") against the simple class name. */
    public boolean isNeverQuarantine(TestResult result) {
        String className = result.className();
        String simple = className.substring(className.lastIndexOf('.') + 1);
        for (String glob : neverQuarantine) {
            String regex = glob.replace(".", "\\.").replace("*", ".*");
            if (simple.matches(regex) || className.matches(regex)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> section(Map<String, Object> root, String name) {
        Object value = root.get(name);
        if (!(value instanceof Map)) {
            throw new IllegalStateException("quality-gates.yml is missing the '" + name + "' section");
        }
        return (Map<String, Object>) value;
    }

    private static double number(Map<String, Object> section, String key) {
        Object value = section.get(key);
        if (!(value instanceof Number n)) {
            throw new IllegalStateException("quality-gates.yml: '" + key + "' must be a number");
        }
        return n.doubleValue();
    }
}
