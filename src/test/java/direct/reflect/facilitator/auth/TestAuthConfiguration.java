package direct.reflect.facilitator.auth;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

/**
 * Test configuration that imports the TestAuthController for integration tests.
 */
@TestConfiguration
@Import(TestAuthController.class)
public class TestAuthConfiguration {
}