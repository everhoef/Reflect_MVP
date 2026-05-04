package direct.reflect.facilitator.common;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ArchitectureGuardrailTest {

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

}
