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
        assertEquals("Circle of Questions", defaultTemplate.getDecideActions().getName());

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
        
        // Count by step type to understand what we have
        long instructionSteps = retroStepRepository.findAll().stream()
                .filter(step -> step.getStepType() == StepType.INSTRUCTION).count();
        long activitySteps = retroStepRepository.findAll().stream()
                .filter(step -> step.getStepType() == StepType.ACTIVITY).count();
        long discussionSteps = retroStepRepository.findAll().stream()
                .filter(step -> step.getStepType() == StepType.DISCUSSION).count();
                
        log.info("Step distribution - Instruction: {}, Activity: {}, Discussion: {}", 
                instructionSteps, activitySteps, discussionSteps);
        
        // For debugging, let's verify the expected total matches what we imported
        assertTrue(totalSteps > 0, "Should have imported some steps");
        assertEquals(81, totalSteps, "Should have imported all 81 steps from CSV");
        
        // Test RetroSteps for each RetroStage, ensuring they are properly ordered
        for (RetroStage stage : stages) {
            List<RetroStep> stepsForStage = retroStepRepository.findByRetroStageOrderByOrderIndexAsc(stage);
            
            // Verify each stage has at least one step and steps are properly ordered
            assertTrue(stepsForStage.size() > 0, 
                "Stage " + stage.getName() + " should have at least one step");
            
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
                assertNotNull(step.getStepType(), "Step should have a step type");
                assertNotNull(step.getTitle(), "Step should have a title");
            }
            
            // Verify only ACTIVITY steps have dataPattern set (if any exist)
            for (RetroStep step : stepsForStage) {
                if (step.getStepType() == StepType.ACTIVITY) {
                    assertNotNull(step.getDataPattern(), 
                        "ACTIVITY step should have dataPattern in stage " + stage.getName());
                } else {
                    assertNull(step.getDataPattern(), 
                        step.getStepType() + " step should not have dataPattern in stage " + stage.getName());
                }
            }
        }
    }

    @Test
    @Transactional
    void testDataPatternDistribution() {
        List<RetroStep> activitySteps = retroStepRepository.findAll().stream()
                .filter(step -> step.getStepType() == StepType.ACTIVITY)
                .toList();
        
        long categoricalCount = activitySteps.stream()
                .filter(step -> step.getDataPattern() == DataPattern.CATEGORICAL)
                .count();
        long ratingCount = activitySteps.stream()
                .filter(step -> step.getDataPattern() == DataPattern.RATING)
                .count();  
        long freeformCount = activitySteps.stream()
                .filter(step -> step.getDataPattern() == DataPattern.FREEFORM)
                .count();
        
        // Verify we have a good distribution of patterns
        assertTrue(categoricalCount > 0, "Should have CATEGORICAL patterns");
        assertTrue(ratingCount > 0, "Should have RATING patterns");
        assertTrue(freeformCount > 0, "Should have FREEFORM patterns");
        
        log.info("Pattern distribution - CATEGORICAL: {}, RATING: {}, FREEFORM: {}", 
                categoricalCount, ratingCount, freeformCount);
        
        // Total should equal number of ACTIVITY steps imported (33 based on CSV data)
        assertEquals(33, categoricalCount + ratingCount + freeformCount);
    }

    @Test
    @Transactional  
    void testSpecificStageConfiguration() {
        // Test Mad Sad Glad configuration
        RetroStage madSadGlad = retroStageRepository.findAll().stream()
                .filter(s -> s.getName().equals("Mad Sad Glad"))
                .findFirst().orElse(null);
        assertNotNull(madSadGlad);
        
        List<RetroStep> steps = retroStepRepository.findByRetroStageOrderByOrderIndexAsc(madSadGlad);
        RetroStep activityStep = steps.get(1);
        assertEquals(DataPattern.CATEGORICAL, activityStep.getDataPattern());
        assertTrue(activityStep.getConfiguration().contains("Mad"));
        assertTrue(activityStep.getConfiguration().contains("Sad"));  
        assertTrue(activityStep.getConfiguration().contains("Glad"));
        
        // Test Happiness Histogram configuration
        RetroStage happinessHistogram = retroStageRepository.findAll().stream()
                .filter(s -> s.getName().equals("Happiness Histogram"))
                .findFirst().orElse(null);
        assertNotNull(happinessHistogram);
        
        List<RetroStep> happinessSteps = retroStepRepository.findByRetroStageOrderByOrderIndexAsc(happinessHistogram);
        RetroStep happinessActivity = happinessSteps.get(1);
        assertEquals(DataPattern.RATING, happinessActivity.getDataPattern());
        assertTrue(happinessActivity.getConfiguration().contains("scale"));
    }
}
