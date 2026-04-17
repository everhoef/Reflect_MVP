package direct.reflect.facilitator.configurator;

import direct.reflect.facilitator.eventing.EventService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@ActiveProfiles("import")
@Slf4j
class TemplateImportIntegrationTest {

    @MockitoBean
    private EventService eventService;

    @Container
    @ServiceConnection
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("postgres")
            .withUsername("postgres")
            .withPassword("postgres");

    @Container
    @ServiceConnection
    @SuppressWarnings("resource")
    static GenericContainer<?> redisContainer = new GenericContainer<>("redis:alpine")
            .withExposedPorts(6379);

    @Autowired
    private RetroStageRepository retroStageRepository;

    @Autowired
    private RetroStepRepository retroStepRepository;

    @Test
    @Transactional
    void stage21_shouldExistInStagesCsv() {
        RetroStage stage21 = retroStageRepository.findAll().stream()
                .filter(s -> s.getMastersheetID() != null && s.getMastersheetID() == 21)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Stage with mastersheetID=21 (Start Stop Keep) not found"));

        log.info("Found stage: {} (mastersheetID={})", stage21.getName(), stage21.getMastersheetID());
        assertThat(stage21.getName()).as("Stage 21 should be named Start Stop Keep").isEqualTo("Start Stop Keep");
    }

    @Test
    @Transactional
    void stage21_shouldHaveNoSteps() {
        RetroStage stage21 = retroStageRepository.findAll().stream()
                .filter(s -> s.getMastersheetID() != null && s.getMastersheetID() == 21)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Stage with mastersheetID=21 (Start Stop Keep) not found"));

        List<RetroStep> steps = retroStepRepository.findByRetroStageOrderByOrderIndexAsc(stage21);
        log.info("Steps found for stage 21: {}", steps.size());

        assertThat(steps)
                .as("Stage 21 (Start Stop Keep) should have no steps — step rows were intentionally removed " +
                    "when the Default template was redesigned to use stage 29 (Start Stop Continue)")
                .isEmpty();
    }

    @Test
    @Transactional
    void stage29_shouldHaveExactly7Steps() {
        RetroStage stage29 = retroStageRepository.findAll().stream()
                .filter(s -> s.getMastersheetID() != null && s.getMastersheetID() == 29)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Stage with mastersheetID=29 (Start Stop Continue) not found"));

        log.info("Found stage: {} (mastersheetID={})", stage29.getName(), stage29.getMastersheetID());

        List<RetroStep> steps = retroStepRepository.findByRetroStageOrderByOrderIndexAsc(stage29);
        log.info("Steps found for stage 29: {}", steps.size());

        assertThat(steps)
                .as("Stage 29 (Start Stop Continue) should have exactly 7 steps")
                .hasSize(7);
    }

    @Test
    @Transactional
    void stage29_step6_shouldBeSmartActionBuilder() {
        RetroStage stage29 = retroStageRepository.findAll().stream()
                .filter(s -> s.getMastersheetID() != null && s.getMastersheetID() == 29)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Stage with mastersheetID=29 (Start Stop Continue) not found"));

        List<RetroStep> steps = retroStepRepository.findByRetroStageOrderByOrderIndexAsc(stage29);
        assertThat(steps)
                .as("Stage 29 should expose at least 6 ordered steps before checking step 6")
                .hasSizeGreaterThanOrEqualTo(6);
        RetroStep step6 = steps.get(5);

        assertThat(step6.getOrderIndex()).isEqualTo(6);
        assertThat(step6.getComponentType()).isEqualTo(ComponentType.SMART_ACTION_BUILDER);
        assertThat(step6.getAdvancementTrigger()).isEqualTo(AdvancementTrigger.FACILITATOR_CLICK);
        
        Map<String, Object> config = step6.getComponentConfig();
        assertThat(config).containsKey("templates");
        assertThat(config).containsKey("categories");
        assertThat(config.get("allowEscalation")).isEqualTo(true);
    }

    @Test
    @Transactional
    void stage31_shouldExistWithActionReviewStep() {
        RetroStage stage31 = retroStageRepository.findAll().stream()
                .filter(s -> s.getMastersheetID() != null && s.getMastersheetID() == 31)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Stage with mastersheetID=31 (Action Review) not found"));

        assertThat(stage31.getName()).isEqualTo("Action Review");

        List<RetroStep> steps = retroStepRepository.findByRetroStageOrderByOrderIndexAsc(stage31);
        assertThat(steps).hasSize(1);
        assertThat(steps.get(0).getComponentType()).isEqualTo(ComponentType.ACTION_REVIEW);
    }

    @Test
    @Transactional
    void stage29_allStepsExceptStep6ShouldBeMultiColumnBoard() {
        RetroStage stage29 = retroStageRepository.findAll().stream()
                .filter(s -> s.getMastersheetID() != null && s.getMastersheetID() == 29)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Stage with mastersheetID=29 (Start Stop Continue) not found"));

        List<RetroStep> steps = retroStepRepository.findByRetroStageOrderByOrderIndexAsc(stage29);

        for (int i = 0; i < steps.size(); i++) {
            if (i == 5) continue;
            assertThat(steps.get(i).getComponentType())
                    .as("Step %d should use MULTI_COLUMN_BOARD", i + 1)
                    .isEqualTo(ComponentType.MULTI_COLUMN_BOARD);
        }
    }

    @Test
    @Transactional
    void stage29_firstStepShouldBeInputMode() {
        RetroStage stage29 = retroStageRepository.findAll().stream()
                .filter(s -> s.getMastersheetID() != null && s.getMastersheetID() == 29)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Stage with mastersheetID=29 (Start Stop Continue) not found"));

        List<RetroStep> steps = retroStepRepository.findByRetroStageOrderByOrderIndexAsc(stage29);
        assertThat(steps).isNotEmpty();

        RetroStep firstStep = steps.get(0);
        assertThat(firstStep.getOrderIndex()).isEqualTo(1);

        Map<String, Object> config = firstStep.getComponentConfig();
        assertThat(config).containsKey("capabilities");

        @SuppressWarnings("unchecked")
        Map<String, Object> capabilities = (Map<String, Object>) config.get("capabilities");

        assertThat(capabilities.get("allowInput"))
                .as("First step (orderIndex=1) should have allowInput=true (input mode)")
                .isEqualTo(true);

        assertThat(capabilities.get("showContent"))
                .as("First step (orderIndex=1) should have showContent=false (private during input)")
                .isEqualTo(false);
    }

    @Test
    @Transactional
    void stage29_shouldHaveAtLeastOneVotingStep() {
        RetroStage stage29 = retroStageRepository.findAll().stream()
                .filter(s -> s.getMastersheetID() != null && s.getMastersheetID() == 29)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Stage with mastersheetID=29 (Start Stop Continue) not found"));

        List<RetroStep> steps = retroStepRepository.findByRetroStageOrderByOrderIndexAsc(stage29);

        long votingSteps = steps.stream()
                .filter(step -> {
                    Map<String, Object> config = step.getComponentConfig();
                    if (config == null || !config.containsKey("capabilities")) return false;
                    @SuppressWarnings("unchecked")
                    Map<String, Object> capabilities = (Map<String, Object>) config.get("capabilities");
                    return Boolean.TRUE.equals(capabilities.get("allowVoting"));
                })
                .count();

        log.info("Steps with allowVoting=true: {}", votingSteps);

        assertThat(votingSteps)
                .as("Stage 29 should have at least one step with allowVoting=true")
                .isGreaterThanOrEqualTo(1);
    }

    @Test
    @Transactional
    void stage29_stepsShouldHaveAscendingOrderIndices() {
        RetroStage stage29 = retroStageRepository.findAll().stream()
                .filter(s -> s.getMastersheetID() != null && s.getMastersheetID() == 29)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Stage with mastersheetID=29 (Start Stop Continue) not found"));

        List<RetroStep> steps = retroStepRepository.findByRetroStageOrderByOrderIndexAsc(stage29);

        for (int i = 0; i < steps.size(); i++) {
            assertThat(steps.get(i).getOrderIndex())
                    .as("Step at position %d should have orderIndex=%d", i, i + 1)
                    .isEqualTo(i + 1);
        }
    }
}
