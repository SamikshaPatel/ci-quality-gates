package io.github.samikshapatel.qg.gate;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Tags each failure ENV (the environment broke: timeouts, 5xx, unreachable host) or CODE
 * (the product broke: an assertion failed against a healthy environment).
 * The tag decides the owner, so DevOps triages ENV without asking QA.
 */
public final class Classifier {

    public enum Kind { ENV, CODE, MIXED, NONE }

    private Classifier() {
    }

    public static Kind classify(TestResult result, List<Pattern> envPatterns) {
        String text = result.failureText();
        for (Pattern p : envPatterns) {
            if (p.matcher(text).find()) {
                return Kind.ENV;
            }
        }
        return Kind.CODE;
    }

    /** All ENV → ENV, all CODE → CODE, both → MIXED, no failures → NONE. */
    public static Kind overall(List<TestResult> failures, List<Pattern> envPatterns) {
        if (failures.isEmpty()) {
            return Kind.NONE;
        }
        boolean anyEnv = false;
        boolean anyCode = false;
        for (TestResult f : failures) {
            if (classify(f, envPatterns) == Kind.ENV) {
                anyEnv = true;
            } else {
                anyCode = true;
            }
        }
        if (anyEnv && anyCode) {
            return Kind.MIXED;
        }
        return anyEnv ? Kind.ENV : Kind.CODE;
    }
}
