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

    /** First line of the failure message, trimmed for reports. */
    public String shortMessage() {
        String text = failureText();
        int newline = text.indexOf('\n');
        String first = newline >= 0 ? text.substring(0, newline) : text;
        return first.length() > 160 ? first.substring(0, 157) + "..." : first;
    }
}
