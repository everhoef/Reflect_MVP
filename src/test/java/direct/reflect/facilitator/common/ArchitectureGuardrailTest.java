package direct.reflect.facilitator.common;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import direct.reflect.facilitator.e2e.support.BaseEndToEndTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ArchitectureGuardrailTest {

    private static final Path PROJECT_ROOT = Paths.get(".").toAbsolutePath().normalize();
    private static final Path BDD_STEP_DEFINITIONS = PROJECT_ROOT.resolve("src/test/java/direct/reflect/facilitator/bdd/stepdefinitions");
    private static final Path BDD_SUPPORT_DRIVERS = PROJECT_ROOT.resolve("src/test/java/direct/reflect/facilitator/bdd/support/drivers");
    private static final Path FEATURE_FILES = PROJECT_ROOT.resolve("src/test/resources/features");

    private static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("direct.reflect.facilitator");

    private static final JavaClasses TEST_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.ONLY_INCLUDE_TESTS)
            .importPackages("direct.reflect.facilitator");

    private static final DescribedPredicate<JavaClass> ARCHITECTURE_GUARDRAIL_TESTS =
            new DescribedPredicate<>("have an architecture guardrail test name") {
                @Override
                public boolean test(JavaClass input) {
                    String simpleName = input.getSimpleName();
                    return simpleName.contains("Architecture")
                            || simpleName.contains("Guardrail")
                            || simpleName.contains("BrowserOnly");
                }
            };

    @Test
    @DisplayName("browser tests stay in e2e or bdd package only")
    void browserTestsStayInE2ePackageOnly() {
        ArchRule rule = noClasses()
                .that().resideOutsideOfPackages("..e2e..", "..bdd..")
                .should().dependOnClassesThat().resideInAnyPackage("com.microsoft.playwright..")
                .as("browser tests stay in e2e or bdd package only")
                .because("Playwright-dependent test code belongs in direct.reflect.facilitator.e2e (Playwright E2E journeys) or direct.reflect.facilitator.bdd (Cucumber BDD step definitions) so module packages stay focused on non-UI correctness.");

        rule.check(TEST_CLASSES);
    }

    @Test
    @DisplayName("architecture guardrail tests live in the common test area")
    void architectureGuardrailTestsLiveInTheCommonTestArea() {
        ArchRule rule = classes()
                .that(ARCHITECTURE_GUARDRAIL_TESTS)
                .should().resideInAPackage("..common..")
                .as("architecture guardrail tests live in the common test area")
                .because("Repo-wide architecture enforcement should stay under direct.reflect.facilitator.common instead of drifting into feature packages.");

        rule.check(TEST_CLASSES);
    }

    @Test
    @DisplayName("module root must not grow new dto junk drawers")
    void moduleRootMustNotGrowNewDtoJunkDrawers() {
        ArchRule rule = noClasses()
                .should().resideInAnyPackage(
                        "direct.reflect.facilitator.auth.dto..",
                        "direct.reflect.facilitator.configurator.dto..",
                        "direct.reflect.facilitator.organization.dto..",
                        "direct.reflect.facilitator.eventing.dto..",
                        "direct.reflect.facilitator.common.dto..",
                        "direct.reflect.facilitator.web.dto..")
                .as("module root must not grow new dto junk drawers")
                .because("The current facilitation.dto package is a temporary pilot exception; do not add new top-level dto packages elsewhere before ownership is cleaned up.");

        rule.check(PRODUCTION_CLASSES);
    }

    @Test
    @DisplayName("pilot escalation domain layer stays Spring-free")
    void pilotEscalationDomainLayerStaysSpringFree() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("..facilitation.escalation.domain..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework..")
                .as("pilot domain layer stays Spring-free")
                .because("The facilitation/escalation pilot should only add a domain package when its policy stays framework-free and easy to extract later.");

        rule.check(PRODUCTION_CLASSES);
    }

    @Test
    @DisplayName("configurator must stay out of facilitation runtime collaboration internals")
    void configuratorMustStayOutOfFacilitationRuntimeCollaborationInternals() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("..configurator..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..facilitation.participant..",
                        "..facilitation.response..",
                        "..facilitation.actions..",
                        "..facilitation.clustering..",
                        "..facilitation.escalation..")
                .as("configurator must stay out of facilitation runtime collaboration internals")
                .because("configurator owns templates and import mechanics, not participant, response, voting, clustering, or escalation runtime behavior.");

        rule.check(PRODUCTION_CLASSES);
    }

    @Test
    @DisplayName("eventing must stay out of configurator and organization internals")
    void eventingMustStayOutOfConfiguratorAndOrganizationInternals() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("..eventing..")
                .should().dependOnClassesThat().resideInAnyPackage("..configurator..", "..organization..")
                .as("eventing must stay out of configurator and organization internals")
                .because("eventing owns SSE transport and should not couple itself to template import concerns or organization management internals.");

        rule.check(PRODUCTION_CLASSES);
    }

    @Test
    @DisplayName("common must stay tiny and dependency light")
    void commonMustStayTinyAndDependencyLight() {
        ArchRule rule = classes()
                .that().resideInAPackage("..common..")
                .should().onlyDependOnClassesThat().resideInAnyPackage(
                        "java..",
                        "javax..",
                        "jakarta..",
                        "org.slf4j..",
                        "lombok..",
                        "com.tngtech.archunit..",
                        "org.hibernate..",
                        "com.fasterxml.uuid..",
                        "direct.reflect.facilitator.common.."
                )
                .as("common must stay tiny and dependency light")
                .because("direct.reflect.facilitator.common is the shared kernel and must not depend on any higher-level module.");

        rule.check(PRODUCTION_CLASSES);
    }

    @Test
    @DisplayName("session shell must stay out of downstream facilitation capabilities")
    void sessionShellMustStayOutOfDownstreamFacilitationCapabilities() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("..facilitation.session..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..facilitation.actions..",
                        "..facilitation.clustering..",
                        "..facilitation.escalation..")
                .as("session shell must stay out of downstream facilitation capabilities")
                .because("session owns flow and lifecycle, while action tracking, clustering, and escalation stay in their own facilitation capabilities.");

        rule.check(PRODUCTION_CLASSES);
    }

    @Test
    @DisplayName("no bdd class extends BaseEndToEndTest")
    void noBddClassExtendsBaseEndToEndTest() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("..bdd..")
                .should().beAssignableTo(BaseEndToEndTest.class)
                .as("no bdd class extends BaseEndToEndTest")
                .because("BDD support and step definitions must not inherit the Playwright end-to-end base class.");

        rule.check(TEST_CLASSES);
    }

    @Test
    @DisplayName("no bdd class uses PendingException")
    void noBddClassUsesPendingException() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("..bdd..")
                .should().dependOnClassesThat().haveFullyQualifiedName("io.cucumber.java.PendingException")
                .as("no bdd class uses PendingException")
                .because("Committed BDD code must not ship placeholder pending steps.");

        rule.check(TEST_CLASSES);
    }

    @Test
    @DisplayName("no raw Playwright waits in bdd step definitions")
    void noRawPlaywrightWaitsInBddStepDefinitions() {
        long fileCount = walkFiles(BDD_STEP_DEFINITIONS, "*.java").size();
        Assertions.assertTrue(
                fileCount > 0,
                "Expected at least one .java file in BDD step definitions directory, but found none — check path: " + BDD_STEP_DEFINITIONS);

        List<String> violations = findMatchingLines(
                List.of(BDD_STEP_DEFINITIONS),
                "*.java",
                Pattern.compile("page\\.locator|waitForSelector|waitForFunction|waitForTimeout|Thread\\.sleep"));

        Assertions.assertTrue(
                violations.isEmpty(),
                () -> "Found raw Playwright waits in bdd step definitions:\n" + String.join("\n", violations));
    }

    @Test
    @DisplayName("no css or layout coupling tokens in bdd step definitions or drivers")
    void noCssOrLayoutCouplingTokensInBddStepDefinitionsOrDrivers() {
        long stepDefinitionFileCount = walkFiles(BDD_STEP_DEFINITIONS, "*.java").size();
        Assertions.assertTrue(
                stepDefinitionFileCount > 0,
                "Expected at least one .java file in BDD step definitions directory, but found none — check path: " + BDD_STEP_DEFINITIONS);

        long driverFileCount = walkFiles(BDD_SUPPORT_DRIVERS, "*.java").size();
        Assertions.assertTrue(
                driverFileCount > 0,
                "Expected at least one .java file in BDD support drivers directory, but found none — check path: " + BDD_SUPPORT_DRIVERS);

        List<String> violations = findMatchingLines(
                List.of(BDD_STEP_DEFINITIONS, BDD_SUPPORT_DRIVERS),
                "*.java",
                Pattern.compile("bg-amber|bg-gray|rounded-full|h-px|boundingBox|nth-child"));

        Assertions.assertTrue(
                violations.isEmpty(),
                () -> "Found CSS or layout coupling tokens in bdd step definitions or drivers:\n" + String.join("\n", violations));
    }

    @Test
    @DisplayName("every feature file has a required pilot or facilitation tag")
    void everyFeatureFileHasRequiredTag() {
        long fileCount = walkFiles(FEATURE_FILES, "*.feature").size();
        Assertions.assertTrue(
                fileCount > 0,
                "Expected at least one .feature file in feature directory, but found none — check path: " + FEATURE_FILES);

        List<String> missingTags = findFeatureFilesMissingRequiredTag();

        Assertions.assertTrue(
                missingTags.isEmpty(),
                () -> "Feature files missing @facilitation or @visual-clue-pilot:\n" + String.join("\n", missingTags));
    }

    private List<String> findMatchingLines(List<Path> roots, String fileSuffix, Pattern pattern) {
        return roots.stream()
                .flatMap(root -> walkFiles(root, fileSuffix).stream())
                .sorted()
                .flatMap(path -> readLines(path).stream()
                        .flatMap(indexedLine -> pattern.matcher(indexedLine.content()).find()
                                ? Stream.of(formatViolation(path, indexedLine))
                                : Stream.empty()))
                .toList();
    }

    private List<String> findFeatureFilesMissingRequiredTag() {
        return walkFiles(FEATURE_FILES, "*.feature").stream()
                .sorted(Comparator.naturalOrder())
                .filter(path -> readLines(path).stream().noneMatch(indexedLine ->
                        indexedLine.content().contains("@facilitation")
                                || indexedLine.content().contains("@visual-clue-pilot")))
                .map(PROJECT_ROOT::relativize)
                .map(Path::toString)
                .toList();
    }

    private List<Path> walkFiles(Path root, String glob) {
        if (Files.notExists(root)) {
            return List.of();
        }

        try (Stream<Path> paths = Files.walk(root)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileSystem().getPathMatcher("glob:" + glob).matches(path.getFileName()))
                    .toList();
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to walk " + root, exception);
        }
    }

    private List<IndexedLine> readLines(Path path) {
        try {
            List<String> lines = Files.readAllLines(path);
            return IntStream.range(0, lines.size())
                    .mapToObj(index -> new IndexedLine(index + 1, lines.get(index)))
                    .toList();
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to read " + path, exception);
        }
    }

    private String formatViolation(Path path, IndexedLine indexedLine) {
        return PROJECT_ROOT.relativize(path) + ":" + indexedLine.number() + ": " + indexedLine.content();
    }

    private record IndexedLine(int number, String content) {
    }

}
