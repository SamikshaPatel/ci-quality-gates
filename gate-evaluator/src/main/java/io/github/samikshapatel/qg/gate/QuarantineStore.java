package io.github.samikshapatel.qg.gate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** quarantine.json: the tests currently skipped by QuarantineCondition, with why and since when. */
public class QuarantineStore {

    public static class Entry {
        public String id;
        public String since;
        public String reason;
        public String jira;
        public int greenStreak;
        public String quarantinedBy;

        public Entry() {
        }

        public Entry(String id, String since, String reason, String jira, String quarantinedBy) {
            this.id = id;
            this.since = since;
            this.reason = reason;
            this.jira = jira;
            this.quarantinedBy = quarantinedBy;
        }
    }

    public static class Data {
        public List<Entry> tests = new ArrayList<>();
    }

    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private final Path file;
    private final Data data;

    private QuarantineStore(Path file, Data data) {
        this.file = file;
        this.data = data;
    }

    public static QuarantineStore load(Path file) {
        try {
            Data data = Files.exists(file) ? MAPPER.readValue(file.toFile(), Data.class) : new Data();
            if (data.tests == null) {
                data.tests = new ArrayList<>();
            }
            return new QuarantineStore(file, data);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + file, e);
        }
    }

    public List<Entry> entries() {
        return data.tests;
    }

    public int size() {
        return data.tests.size();
    }

    public Optional<Entry> find(String id) {
        return data.tests.stream().filter(e -> e.id.equals(id)).findFirst();
    }

    public void add(Entry entry) {
        data.tests.add(entry);
    }

    public void remove(String id) {
        data.tests.removeIf(e -> e.id.equals(id));
    }

    public void save() {
        try {
            Files.writeString(file, MAPPER.writeValueAsString(data) + System.lineSeparator());
        } catch (IOException e) {
            throw new IllegalStateException("Cannot write " + file, e);
        }
    }
}
