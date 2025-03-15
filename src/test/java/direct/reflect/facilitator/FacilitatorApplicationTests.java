package direct.reflect.facilitator;

import direct.reflect.facilitator.config.TestDatabaseConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@Import(TestDatabaseConfiguration.class)
@ActiveProfiles("test")
class FacilitatorApplicationTests {

    @Test
    void contextLoads() {
        // Context loading is sufficient
    }
}
