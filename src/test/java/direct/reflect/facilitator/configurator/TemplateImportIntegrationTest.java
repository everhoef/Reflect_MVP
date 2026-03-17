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
    void stage21_shouldHaveExactly40Steps() {
        RetroStage stage21 = retroStageRepository.findAll().stream()
                .filter(s -> s.getMastersheetID() != null && s.getMastersheetID() == 21)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Stage with mastersheetID=21 (Start Stop Keep) not found"));

        log.info("Found stage: {} (id={})", stage21.getName(), stage21.getMastersheetID());

        List<RetroStep> steps = retroStepRepository.findByRetroStageOrderByOrderIndexAsc(stage21);
        log.info("Steps found for stage 21: {}", steps.size());

        assertThat(steps)
                .as("Stage 21 (Start Stop Keep) should have exactly 40 steps")
                .hasSize(40);
    }

    @Test
    @Transactional
    void stage21_allStepsShouldBeMultiColumnBoard() {
        RetroStage stage21 = retroStageRepository.findAll().stream()
                .filter(s -> s.getMastersheetID() != null && s.getMastersheetID() == 21)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Stage with mastersheetID=21 (Start Stop Keep) not found"));

        List<RetroStep> steps = retroStepRepository.findByRetroStageOrderByOrderIndexAsc(stage21);

        assertThat(steps)
                .as("All SSC steps should use MULTI_COLUMN_BOARD component type")
                .allMatch(step -> step.getComponentType() == ComponentType.MULTI_COLUMN_BOARD);
    }

    @Test
    @Transactional
    void stage21_firstStepShouldBeInstructionsMode() {
        RetroStage stage21 = retroStageRepository.findAll().stream()
                .filter(s -> s.getMastersheetID() != null && s.getMastersheetID() == 21)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Stage with mastersheetID=21 (Start Stop Keep) not found"));

        List<RetroStep> steps = retroStepRepository.findByRetroStageOrderByOrderIndexAsc(stage21);
        assertThat(steps).isNotEmpty();

        RetroStep firstStep = steps.get(0);
        assertThat(firstStep.getOrderIndex()).isEqualTo(1);

        Map<String, Object> config = firstStep.getComponentConfig();
        assertThat(config).containsKey("capabilities");

        @SuppressWarnings("unchecked")
        Map<String, Object> capabilities = (Map<String, Object>) config.get("capabilities");

        assertThat(capabilities.get("showContent"))
                .as("First step (orderIndex=1) should have showContent=true (INSTRUCTIONS mode)")
                .isEqualTo(true);

        assertThat(capabilities.get("allowInput"))
                .as("First step (orderIndex=1) should have allowInput=false (INSTRUCTIONS mode)")
                .isEqualTo(false);
    }

    @Test
    @Transactional
    void stage21_shouldHaveAtLeastOneVotingStep() {
        RetroStage stage21 = retroStageRepository.findAll().stream()
                .filter(s -> s.getMastersheetID() != null && s.getMastersheetID() == 21)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Stage with mastersheetID=21 (Start Stop Keep) not found"));

        List<RetroStep> steps = retroStepRepository.findByRetroStageOrderByOrderIndexAsc(stage21);

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
                .as("Stage 21 should have at least one step with allowVoting=true")
                .isGreaterThanOrEqualTo(1);
    }

    @Test
    @Transactional
    void stage21_stepsShouldHaveAscendingOrderIndices() {
        RetroStage stage21 = retroStageRepository.findAll().stream()
                .filter(s -> s.getMastersheetID() != null && s.getMastersheetID() == 21)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Stage with mastersheetID=21 (Start Stop Keep) not found"));

        List<RetroStep> steps = retroStepRepository.findByRetroStageOrderByOrderIndexAsc(stage21);

        for (int i = 0; i < steps.size(); i++) {
            assertThat(steps.get(i).getOrderIndex())
                    .as("Step at position %d should have orderIndex=%d", i, i + 1)
                    .isEqualTo(i + 1);
        }
    }
}
