package direct.reflect.facilitator.configurator;

import direct.reflect.facilitator.eventing.EventService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
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
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles({"import"})
@Testcontainers
@Slf4j
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
    
    @Autowired
    private RetroStepRepository retroStepRepository;

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
        assertEquals("Happiness Histogram", defaultTemplate.getSetTheStage().getName());

        assertNotNull(defaultTemplate.getGatherData());
        assertEquals("Mad Sad Glad", defaultTemplate.getGatherData().getName());

        assertNotNull(defaultTemplate.getGenerateInsights());
        assertEquals("The Original Four", defaultTemplate.getGenerateInsights().getName());

        assertNotNull(defaultTemplate.getDecideActions());
        assertEquals("Start Stop Keep", defaultTemplate.getDecideActions().getName());

        assertNotNull(defaultTemplate.getCloseRetro());
        assertEquals("Feedback Door Smiley's", defaultTemplate.getCloseRetro().getName());
    }

    @Test
    @Transactional
    void testImportRetroStages() {
        List<RetroStage> stages = retroStageRepository.findAll();
        assertEquals(26, stages.size());
        
        // Verify some key stages are imported correctly
        RetroStage madSadGlad = stages.stream()
                .filter(s -> s.getName().equals("Mad Sad Glad"))
                .findFirst().orElse(null);
        assertNotNull(madSadGlad);
        assertNotNull(madSadGlad.getWhy());
        assertNotNull(madSadGlad.getWhat());
        
        RetroStage happinessHistogram = stages.stream()
                .filter(s -> s.getName().equals("Happiness Histogram"))
                .findFirst().orElse(null);
        assertNotNull(happinessHistogram);
        assertNotNull(happinessHistogram.getWhy());
    }

    @Test
    @Transactional
    void testImportRetroStepsOrderedByStage() {
        List<RetroStage> stages = retroStageRepository.findAll();
        assertEquals(26, stages.size());

        long totalSteps = retroStepRepository.count();
        log.info("Total steps found: {}", totalSteps);

        // Count by component type to understand what we have
        long boardSteps = retroStepRepository.findAll().stream()
                .filter(step -> step.getComponentType() == ComponentType.MULTI_COLUMN_BOARD).count();
        long ratingSteps = retroStepRepository.findAll().stream()
                .filter(step -> step.getComponentType() == ComponentType.RATING_SCALE).count();
        long histogramSteps = retroStepRepository.findAll().stream()
                .filter(step -> step.getComponentType() == ComponentType.HISTOGRAM_CHART).count();

        log.info("Component distribution - Board: {}, Rating: {}, Histogram: {}",
                boardSteps, ratingSteps, histogramSteps);

        // For debugging, let's verify the expected total matches what we imported
        assertTrue(totalSteps > 0, "Should have imported some steps");
        assertEquals(64, totalSteps, "Should have imported all 64 steps from CSV");
        assertEquals(62, boardSteps, "Should have 62 MULTI_COLUMN_BOARD steps");
        assertEquals(1, ratingSteps, "Should have 1 RATING_SCALE step");
        assertEquals(1, histogramSteps, "Should have 1 HISTOGRAM_CHART step");

        // Test RetroSteps for each RetroStage, ensuring they are properly ordered
        for (RetroStage stage : stages) {
            List<RetroStep> stepsForStage = retroStepRepository.findByRetroStageOrderByOrderIndexAsc(stage);

            // Skip stages with no steps (many stages from old data won't have steps yet)
            if (stepsForStage.isEmpty()) {
                continue;
            }

            log.debug("Stage '{}' has {} steps", stage.getName(), stepsForStage.size());

            // Verify steps are properly ordered by orderIndex (steps are sorted by orderIndex ascending)
            Integer previousOrderIndex = null;
            for (RetroStep step : stepsForStage) {
                if (previousOrderIndex != null) {
                    assertTrue(step.getOrderIndex() > previousOrderIndex,
                        "Step order should be ascending in stage " + stage.getName());
                }
                previousOrderIndex = step.getOrderIndex();
                assertEquals(stage, step.getRetroStage());
                assertNotNull(step.getComponentType(), "Step should have a component type");
                assertNotNull(step.getComponentConfig(), "Step should have component config");
                assertNotNull(step.getAdvancementTrigger(), "Step should have advancement trigger");
            }
        }
    }

}
