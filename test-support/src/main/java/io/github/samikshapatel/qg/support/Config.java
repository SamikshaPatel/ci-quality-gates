package io.github.samikshapatel.qg.support;

/**
 * Test configuration from environment variables (GitHub secrets in CI), with the
 * public demo defaults for local runs. Credentials never live in the repo itself.
 */
public final class Config {

    private Config() {
    }

    public static String get(String envName, String fallback) {
        String fromProperty = System.getProperty(envName);
        if (fromProperty != null && !fromProperty.isBlank()) {
            return fromProperty;
        }
        String fromEnv = System.getenv(envName);
        return (fromEnv == null || fromEnv.isBlank()) ? fallback : fromEnv;
    }

    public static String apiBaseUrl() {
        return get("API_BASE_URL", "http://localhost:3001");
    }

    public static String apiUsername() {
        return get("RB_USERNAME", "admin");
    }

    public static String apiPassword() {
        return get("RB_PASSWORD", "password123");
    }

    public static String uiBaseUrl() {
        return get("UI_BASE_URL", "https://www.saucedemo.com");
    }

    public static String uiUsername() {
        return get("SAUCE_USERNAME", "standard_user");
    }

    public static String uiPassword() {
        return get("SAUCE_PASSWORD", "secret_sauce");
    }
}
