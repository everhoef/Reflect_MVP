package direct.reflect.facilitator.common;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Architectural enforcement test: the integration/ package must contain ONLY browser/E2E tests.
 *
 * <p>Any test class in src/test/java/.../integration/ that does NOT import Playwright
 * (com.microsoft.playwright) is a violation of the browser-only rule.
 *
 * <p>If this test fails, move the offending class to the appropriate module-aligned package
 * (facilitation/, configurator/, auth/, etc.) and update its package declaration.
 */
class IntegrationPackageBrowserOnlyTest {

    private static final String PLAYWRIGHT_IMPORT = "com.microsoft.playwright";

    @Test
    void integrationPackageShouldContainOnlyBrowserTests() throws IOException {
        Path integrationDir = findIntegrationDir();
        if (integrationDir == null || !Files.exists(integrationDir)) {
            return;
        }

        List<Path> violations = Files.walk(integrationDir)
                .filter(p -> p.toString().endsWith(".java"))
                .filter(p -> !p.getFileName().toString().equals("BaseIntegrationTest.java"))
                .filter(p -> !containsPlaywrightImport(p))
                .collect(Collectors.toList());

        assertThat(violations)
                .as("The following files in integration/ do not import Playwright and must be moved " +
                    "to a module-aligned test package (facilitation/, configurator/, auth/, etc.):\n%s",
                    violations.stream().map(Path::getFileName).map(Path::toString).collect(Collectors.joining("\n")))
                .isEmpty();
    }

    private Path findIntegrationDir() {
        String[] candidates = {
            "src/test/java/direct/reflect/facilitator/integration",
            "../src/test/java/direct/reflect/facilitator/integration"
        };
        for (String candidate : candidates) {
            Path p = Paths.get(candidate);
            if (Files.exists(p)) {
                return p;
            }
        }
        Path workDir = Paths.get(System.getProperty("user.dir"));
        Path candidate = workDir.resolve("src/test/java/direct/reflect/facilitator/integration");
        if (Files.exists(candidate)) {
            return candidate;
        }
        return null;
    }

    private boolean containsPlaywrightImport(Path javaFile) {
        try {
            String content = Files.readString(javaFile);
            return content.contains(PLAYWRIGHT_IMPORT);
        } catch (IOException e) {
            return false;
        }
    }
}
