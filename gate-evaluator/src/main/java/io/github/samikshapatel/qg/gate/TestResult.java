package io.github.samikshapatel.qg.gate;

/**
 * One test's outcome, read from Surefire XML.
 * FLAKY means it failed at least once and then passed on a retry.
 */
public record TestResult(String id, String className, String method, Status status, String failureType,
                         String message) {

    public enum Status { PASSED, FAILED, FLAKY, SKIPPED }

    public boolean executed() {
        return status != Status.SKIPPED;
    }

    /** Failure type plus message: the text the ENV/CODE classifier looks at. */
    public String failureText() {
        return ((failureType == null ? "" : failureType) + " " + (message == null ? "" : message)).trim();
    }

    /**
     * The most useful line of the failure, trimmed for reports. Skips RestAssured's
     * "1 expectation failed." preamble so the report shows the actual expectation.
     */
    public String shortMessage() {
        String source = (message == null || message.isBlank()) ? (failureType == null ? "" : failureType) : message;
        String line = source.lines()
                .map(String::trim)
                .filter(l -> !l.isEmpty() && !l.matches("\\d+ expectations? failed\\.?"))
                .findFirst()
                .orElse(source.trim());
        return line.length() > 160 ? line.substring(0, 157) + "..." : line;
    }
}
