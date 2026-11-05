package io.github.samikshapatel.qg.gate;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Impact rules from the real quality-gates.yml, against the kinds of PR a team opens. */
class ImpactPlannerTest {

    private static final ImpactPlanner.Rules RULES = load();

    private static ImpactPlanner.Rules load() {
        try (Reader reader = Files.newBufferedReader(Path.of("../quality-gates.yml"))) {
            Map<String, Object> root = new Yaml().load(reader);
            return ImpactPlanner.rules(root);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ImpactPlanner.Plan plan(String... files) {
        return ImpactPlanner.plan(List.of(files), RULES, f -> true);
    }

    @Test
    void docsOnlyRunsNoTestGates() {
        ImpactPlanner.Plan p = plan("README.md", "docs/adr/0010-impact-based-selection.md", "site/index.html");
        assertEquals("docs", p.scope);
        assertTrue(p.selectedTiers.isEmpty());
        assertTrue(p.stabilityTests.isEmpty());
    }

    @Test
    void apiTestChangeRunsApiModuleAndSmokeEverywhere() {
        ImpactPlanner.Plan p = plan("api-tests/src/test/java/io/github/samikshapatel/qg/api/BookingRegressionTest.java", "README.md");
        assertEquals("api", p.scope);
        assertEquals(List.of("api-tests"), p.modules);
        assertEquals(List.of("smoke", "contract", "critical", "regression"), p.selectedTiers);
        assertEquals(List.of("api-tests|io.github.samikshapatel.qg.api.BookingRegressionTest"), p.stabilityTests);
    }

    @Test
    void uiTestChangeSkipsContract() {
        ImpactPlanner.Plan p = plan("ui-tests/src/test/java/io/github/samikshapatel/qg/ui/StoreRegressionTest.java");
        assertEquals("ui", p.scope);
        assertEquals(List.of("ui-tests"), p.modules);
        assertFalse(p.selected("contract"));
        assertTrue(p.selected("smoke"));
    }

    @Test
    void apiAndUiTogetherRunEverything() {
        ImpactPlanner.Plan p = plan("api-tests/pom.xml", "ui-tests/pom.xml");
        assertEquals("full", p.scope);
        assertEquals(RunReport.ORDER, p.selectedTiers);
    }

    @Test
    void sharedOrPipelineFileRunsEverythingAndNamesIt() {
        ImpactPlanner.Plan p = plan("api-tests/src/test/resources/schemas/booking.json", ".github/workflows/pr.yml");
        assertEquals("full", p.scope);
        assertTrue(p.reason.contains(".github/workflows/pr.yml"), p.reason);
        assertEquals(List.of("api-tests", "ui-tests"), p.modules);
    }

    @Test
    void gateConfigChangeRunsEverything() {
        assertEquals("full", plan("quality-gates.yml").scope);
        assertEquals("full", plan("test-support/src/main/java/io/github/samikshapatel/qg/support/Tier.java").scope);
    }

    @Test
    void noChangedFilesRunsEverythingToBeSafe() {
        assertEquals("full", ImpactPlanner.plan(List.of("", " "), RULES, f -> true).scope);
    }

    @Test
    void stabilityCheckSkipsBaseClassesFaultTestsAndDeletedFiles() {
        ImpactPlanner.Plan p = ImpactPlanner.plan(List.of(
                "api-tests/src/test/java/io/github/samikshapatel/qg/api/BaseApiTest.java",
                "api-tests/src/test/java/io/github/samikshapatel/qg/api/faults/DemoFaultTests.java",
                "api-tests/src/test/java/io/github/samikshapatel/qg/api/OldTest.java",
                "api-tests/src/test/java/io/github/samikshapatel/qg/api/BookingContractTest.java"),
                RULES, f -> !f.endsWith("OldTest.java"));
        assertEquals(List.of("api-tests|io.github.samikshapatel.qg.api.BookingContractTest"), p.stabilityTests);
        assertEquals(3, p.stabilityRuns);
    }

    @Test
    void globsMatchLikeGitHubPaths() {
        assertTrue(ImpactPlanner.glob("*.md").matcher("docs/runbooks/smoke-failure.md").matches());
        assertTrue(ImpactPlanner.glob("docs/**").matcher("docs/adr/0001.md").matches());
        assertFalse(ImpactPlanner.glob("docs/**").matcher("api-tests/docs/x.md").matches());
        assertFalse(ImpactPlanner.glob("api-tests/*").matcher("api-tests/src/A.java").matches());
    }

    @Test
    void envLinesFeedThePipeline() {
        String env = ImpactPlanner.envLines(plan("ui-tests/src/test/java/io/github/samikshapatel/qg/ui/UiSmokeTest.java"));
        assertTrue(env.contains("TEST_SCOPE=ui\n"), env);
        assertTrue(env.contains("SELECTED_TIERS=smoke,critical,regression\n"), env);
        assertTrue(env.contains("TEST_MODULES=ui-tests\n"), env);
        assertTrue(env.contains("STABILITY_TESTS=ui-tests|io.github.samikshapatel.qg.ui.UiSmokeTest\n"), env);
    }

    @Test
    void noteIsOneReadableLine() {
        String note = plan("README.md").note();
        assertTrue(note.startsWith("scope `docs` — Documentation only"), note);
        assertTrue(note.endsWith("(1 changed file)."), note);
    }
}
