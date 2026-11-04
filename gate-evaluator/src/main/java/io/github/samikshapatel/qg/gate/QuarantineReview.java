package io.github.samikshapatel.qg.gate;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Nightly review of quarantine.json. The nightly run includes quarantined tests
 * (-Dinclude.quarantined=true), so each one keeps a green streak:
 * a clean pass adds one, a flaky or failed run resets it to zero.
 * At the exit streak the test is released; past the age limit it is flagged.
 */
public final class QuarantineReview {

    public static class Summary {
        public List<String> released = new ArrayList<>();
        public List<String> overdue = new ArrayList<>();
        public int remaining;
        public int cap;
        public int maxAgeDays;
    }

    private QuarantineReview() {
    }

    public static Summary review(QuarantineStore store, List<TestResult> results, GateConfig config, LocalDate today) {
        Map<String, TestResult> byId = results.stream()
                .collect(Collectors.toMap(TestResult::id, Function.identity(), (a, b) -> b));
        Summary s = new Summary();
        s.cap = config.quarantineMaxTests();
        s.maxAgeDays = config.quarantineMaxAgeDays();

        for (QuarantineStore.Entry e : new ArrayList<>(store.entries())) {
            TestResult r = byId.get(e.id);
            if (r != null) {
                if (r.status() == TestResult.Status.PASSED) {
                    e.greenStreak++;
                } else if (r.status() == TestResult.Status.FAILED || r.status() == TestResult.Status.FLAKY) {
                    e.greenStreak = 0;
                }
            }
            if (e.greenStreak >= config.quarantineExitStreak()) {
                store.remove(e.id);
                s.released.add(e.id);
                continue;
            }
            if (e.since != null && ChronoUnit.DAYS.between(LocalDate.parse(e.since), today) > config.quarantineMaxAgeDays()) {
                s.overdue.add(e.id);
            }
        }
        s.remaining = store.size();
        return s;
    }
}
