package io.github.samikshapatel.qg.gate;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SurefireParserTest {

    private final List<TestResult> results = SurefireParser.parse(List.of(Path.of("src/test/resources/reports")));
    private final Map<String, TestResult> byMethod = results.stream()
            .collect(Collectors.toMap(TestResult::method, Function.identity()));

    @Test
    void readsEveryTestcase() {
        assertEquals(5, results.size());
    }

    @Test
    void passOnRetryIsFlaky() {
        TestResult flaky = byMethod.get("flaky");
        assertEquals(TestResult.Status.FLAKY, flaky.status());
        assertEquals("io.github.samikshapatel.qg.api.faults.DemoFaultTests$NonCriticalFlakeFaults#flaky", flaky.id());
    }

    @Test
    void failOnEveryAttemptIsFailed() {
        TestResult broken = byMethod.get("broken");
        assertEquals(TestResult.Status.FAILED, broken.status());
        assertEquals("java.lang.AssertionError Expected status code <200> but was <504>.", broken.failureText());
    }

    @Test
    void shortMessageSkipsRestAssuredPreamble() {
        TestResult t = new TestResult("a#b", "a", "b", TestResult.Status.FAILED, "java.lang.AssertionError",
                "1 expectation failed.\nExpected status code <200> but was <418>.\n");
        assertEquals("Expected status code <200> but was <418>.", t.shortMessage());
    }

    @Test
    void errorsCountAsFailuresAndSkipsAreSkipped() {
        assertEquals(TestResult.Status.FAILED, byMethod.get("errors").status());
        assertEquals(TestResult.Status.SKIPPED, byMethod.get("skippedOne").status());
        assertEquals(TestResult.Status.PASSED, byMethod.get("passes").status());
    }
}
