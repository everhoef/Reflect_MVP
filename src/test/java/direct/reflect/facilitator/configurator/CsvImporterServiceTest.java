package direct.reflect.facilitator.configurator;

import direct.reflect.facilitator.eventing.EventService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import direct.reflect.facilitator.common.config.SecurityConfig;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles({"import"})
@Testcontainers
class CsvImporterServiceTest {

    @MockitoBean
    private EventService eventService;

    @Container
    @ServiceConnection
    @SuppressWarnings("resource") // Testcontainers manages the lifecycle
    static GenericContainer<?> redis = new GenericContainer<>("redis:alpine")
            .withExposedPorts(6379);

    @Container
    @ServiceConnection
    @SuppressWarnings("resource") // Testcontainers manages the lifecycle
    static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("postgres")
            .withUsername("postgres")
            .withPassword("postgres");

    @Autowired
    private RetroTemplateRepository retroTemplateRepository;

    @Autowired
    private RetroStageRepository retroStageRepository;

    @Test
    @Transactional
    void testImportRetroTemplates() {
        // The import runs automatically on startup because of @PostConstruct and @Profile("import").
        // We just need to verify the results.

        List<RetroTemplate> templates = retroTemplateRepository.findAll();
        assertEquals(2, templates.size());

        RetroTemplate defaultTemplate = templates.stream()
                .filter(t -> t.getName().equals("Default"))
                .findFirst().orElse(null);
        assertNotNull(defaultTemplate);
        assertEquals("Default", defaultTemplate.getName());
        assertEquals("A classic retrospective format covering all five phases.", defaultTemplate.getDescription());
        assertEquals(3, defaultTemplate.getMaturityLevel());
        assertEquals(true, defaultTemplate.isReleased());

        assertNotNull(defaultTemplate.getSetTheStage());
        assertEquals("Hapiness Histogram", defaultTemplate.getSetTheStage().getName());

        assertNotNull(defaultTemplate.getGatherData());
        assertEquals("Mad Sad Glad", defaultTemplate.getGatherData().getName());

        assertNotNull(defaultTemplate.getGenerateInsights());
        assertEquals("The Original Four", defaultTemplate.getGenerateInsights().getName());

        assertNotNull(defaultTemplate.getDecideActions());
        assertEquals("Circle of Questions", defaultTemplate.getDecideActions().getName());

        assertNotNull(defaultTemplate.getCloseRetro());
        assertEquals("Feedback Door Smiley's", defaultTemplate.getCloseRetro().getName());

        assertEquals(26, retroStageRepository.count());
    }
}
