package direct.reflect.facilitator.configurator;

import direct.reflect.facilitator.eventing.EventService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles({"import"})
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Slf4j
class RetroTemplateContractTest {

    private static final Set<ComponentType> SUPPORTED_COMPONENT_TYPES =
            EnumSet.of(ComponentType.MULTI_COLUMN_BOARD, ComponentType.RATING_SCALE, ComponentType.HISTOGRAM_CHART, ComponentType.ESVP_SELECTOR);

    private static final Set<AdvancementTrigger> SUPPORTED_ADVANCEMENT_TRIGGERS =
            EnumSet.of(AdvancementTrigger.AUTO, AdvancementTrigger.FACILITATOR_CLICK,
                       AdvancementTrigger.ALL_RESPONDED, AdvancementTrigger.TIMER_EXPIRES);

    @MockitoBean
    private EventService eventService;

    @Container
    @ServiceConnection
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>("redis:alpine")
            .withExposedPorts(6379);

    @Container
    @ServiceConnection
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("postgres")
            .withUsername("postgres")
            .withPassword("postgres");

    @Autowired
    private RetroTemplateRepository retroTemplateRepository;

    @Autowired
    private RetroStepRepository retroStepRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private final List<Arguments> stepsHolder = new ArrayList<>();

    @BeforeAll
    @org.springframework.transaction.annotation.Transactional
    void loadDefaultTemplateSteps() {
        transactionTemplate.execute(status -> {
            RetroTemplate defaultTemplate = retroTemplateRepository.findAll().stream()
                    .filter(t -> t.getName().equals("Default"))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Default template not found"));

            List<RetroStage> stages = List.of(
                    defaultTemplate.getSetTheStage(),
                    defaultTemplate.getGatherData(),
                    defaultTemplate.getGenerateInsights(),
                    defaultTemplate.getDecideActions(),
                    defaultTemplate.getCloseRetro()
            );

            for (RetroStage stage : stages) {
                List<RetroStep> steps = retroStepRepository.findByRetroStageOrderByOrderIndexAsc(stage);
                for (RetroStep step : steps) {
                    stepsHolder.add(Arguments.of(step.getOrderIndex(), stage.getName(), step));
                }
            }

            log.info("Loaded {} steps from Default template for contract validation", stepsHolder.size());
            return null;
        });
    }

    Stream<Arguments> defaultTemplateSteps() {
        return stepsHolder.stream();
    }

    @ParameterizedTest(name = "step {0} in stage ''{1}'': satisfies capability contract")
    @MethodSource("defaultTemplateSteps")
    void stepSatisfiesCapabilityContract(int orderIndex, String stageName, RetroStep step) {
        String stepLabel = String.format("step %d in stage '%s'", orderIndex, stageName);

        assertNotNull(step.getComponentType(),
                stepLabel + ": componentType must not be null");
        assertTrue(SUPPORTED_COMPONENT_TYPES.contains(step.getComponentType()),
                stepLabel + ": componentType '" + step.getComponentType() + "' not in supported set " + SUPPORTED_COMPONENT_TYPES);

        Map<String, Object> config = step.getComponentConfig();
        assertNotNull(config,
                stepLabel + ": componentConfig must not be null");
        assertFalse(config.isEmpty(),
                stepLabel + ": componentConfig must not be empty");

        assertNotNull(step.getAdvancementTrigger(),
                stepLabel + ": advancementTrigger must not be null");
        assertTrue(SUPPORTED_ADVANCEMENT_TRIGGERS.contains(step.getAdvancementTrigger()),
                stepLabel + ": advancementTrigger '" + step.getAdvancementTrigger() + "' not in supported set " + SUPPORTED_ADVANCEMENT_TRIGGERS);

        if (step.getAdvancementTrigger() == AdvancementTrigger.TIMER_EXPIRES) {
            assertNotNull(step.getDurationSeconds(),
                    stepLabel + ": durationSeconds must not be null when TIMER_EXPIRES");
            assertTrue(step.getDurationSeconds() > 0,
                    stepLabel + ": durationSeconds must be > 0 when TIMER_EXPIRES, was " + step.getDurationSeconds());
        }

        if (step.getComponentType() == ComponentType.MULTI_COLUMN_BOARD) {
            assertTrue(config.containsKey("columns"),
                    stepLabel + ": MULTI_COLUMN_BOARD must have 'columns' key");
            Object columns = config.get("columns");
            assertNotNull(columns, stepLabel + ": 'columns' must not be null");
            assertTrue(columns instanceof List,
                    stepLabel + ": 'columns' must be a List, was " + columns.getClass().getSimpleName());
            assertFalse(((List<?>) columns).isEmpty(),
                    stepLabel + ": 'columns' must not be empty");
        }

        if (step.getComponentType() == ComponentType.RATING_SCALE) {
            assertTrue(config.containsKey("min"),
                    stepLabel + ": RATING_SCALE must have 'min' key");
            assertTrue(config.containsKey("max"),
                    stepLabel + ": RATING_SCALE must have 'max' key");
            assertNotNull(config.get("min"), stepLabel + ": 'min' must not be null");
            assertNotNull(config.get("max"), stepLabel + ": 'max' must not be null");
        }

        if (step.getComponentType() == ComponentType.HISTOGRAM_CHART) {
            assertTrue(config.containsKey("min"),
                    stepLabel + ": HISTOGRAM_CHART must have 'min' key");
            assertTrue(config.containsKey("max"),
                    stepLabel + ": HISTOGRAM_CHART must have 'max' key");
            assertNotNull(config.get("min"), stepLabel + ": 'min' must not be null");
            assertNotNull(config.get("max"), stepLabel + ": 'max' must not be null");
        }

        if (step.getComponentType() == ComponentType.ESVP_SELECTOR) {
            assertTrue(config.containsKey("columns"),
                    stepLabel + ": ESVP_SELECTOR must have 'columns' key");
            Object columns = config.get("columns");
            assertNotNull(columns, stepLabel + ": 'columns' must not be null");
            assertTrue(columns instanceof List,
                    stepLabel + ": 'columns' must be a List, was " + columns.getClass().getSimpleName());
            assertEquals(4, ((List<?>) columns).size(),
                    stepLabel + ": ESVP_SELECTOR must have exactly 4 columns (E/S/V/P)");
        }

        log.debug("✅ {} (type={}, trigger={}) passed contract",
                stepLabel, step.getComponentType(), step.getAdvancementTrigger());
    }
}
