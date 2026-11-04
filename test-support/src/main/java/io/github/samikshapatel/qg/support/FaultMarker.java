package io.github.samikshapatel.qg.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Makes demo fault tests deterministic (ADR-0005).
 *
 * A "flaky" fault test must fail on its first attempt and pass on Surefire's retry,
 * every time — a random 30% failure might not trigger during a live demo.
 * The first call for a given key leaves a marker file and returns true; later calls
 * in the same run return false. Markers live under target/, so every fresh run starts clean.
 */
public final class FaultMarker {

    private FaultMarker() {
    }

    /** True only the first time this key is seen in this run. */
    public static boolean firstAttempt(String key) {
        Path dir = Path.of(System.getProperty("fault.markers.dir", "target/fault-markers"));
        Path marker = dir.resolve(key.replaceAll("[^A-Za-z0-9._-]", "_") + ".marker");
        try {
            Files.createDirectories(dir);
            if (Files.exists(marker)) {
                return false;
            }
            Files.writeString(marker, "first attempt failed on purpose");
            return true;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The demo scenario selected for this run, or "" for a normal run. */
    public static String scenario() {
        return System.getProperty("demo.scenario", "");
    }
}
