package io.github.samikshapatel.qg.gate;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Reads Surefire's TEST-*.xml files.
 *
 * With rerunFailingTestsCount set, Surefire records a test that failed and then passed as a
 * testcase with a flakyFailure/flakyError child, and a test that failed every attempt with
 * failure/error (plus rerunFailure/rerunError). That is how the gate tells flaky from broken.
 */
public final class SurefireParser {

    private SurefireParser() {
    }

    public static List<TestResult> parse(List<Path> reportDirs) {
        List<TestResult> results = new ArrayList<>();
        for (Path dir : reportDirs) {
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> files = Files.list(dir)) {
                for (Path file : files.filter(f -> f.getFileName().toString().matches("TEST-.*\\.xml")).sorted().toList()) {
                    results.addAll(parseFile(file));
                }
            } catch (IOException e) {
                throw new IllegalStateException("Cannot read reports in " + dir, e);
            }
        }
        return results;
    }

    static List<TestResult> parseFile(Path file) {
        List<TestResult> results = new ArrayList<>();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document doc = factory.newDocumentBuilder().parse(file.toFile());
            NodeList cases = doc.getElementsByTagName("testcase");
            for (int i = 0; i < cases.getLength(); i++) {
                results.add(toResult((Element) cases.item(i)));
            }
        } catch (Exception e) {
            throw new IllegalStateException("Cannot parse Surefire report " + file, e);
        }
        return results;
    }

    private static TestResult toResult(Element testcase) {
        String className = testcase.getAttribute("classname");
        String method = testcase.getAttribute("name").replaceAll("\\(.*$", "").trim();
        String id = className + "#" + method;

        Element failure = firstChild(testcase, "failure", "error");
        if (failure != null) {
            return new TestResult(id, className, method, TestResult.Status.FAILED,
                    failure.getAttribute("type"), messageOf(failure));
        }
        Element flaky = firstChild(testcase, "flakyFailure", "flakyError");
        if (flaky != null) {
            return new TestResult(id, className, method, TestResult.Status.FLAKY,
                    flaky.getAttribute("type"), messageOf(flaky));
        }
        if (firstChild(testcase, "skipped") != null) {
            return new TestResult(id, className, method, TestResult.Status.SKIPPED, null, null);
        }
        return new TestResult(id, className, method, TestResult.Status.PASSED, null, null);
    }

    private static String messageOf(Element element) {
        String message = element.getAttribute("message");
        if (message == null || message.isBlank()) {
            message = element.getTextContent();
        }
        return message == null ? "" : message.trim();
    }

    private static Element firstChild(Element parent, String... names) {
        NodeList children = parent.getChildNodes();
        for (String name : names) {
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child instanceof Element e && e.getTagName().equals(name)) {
                    return e;
                }
            }
        }
        return null;
    }
}
