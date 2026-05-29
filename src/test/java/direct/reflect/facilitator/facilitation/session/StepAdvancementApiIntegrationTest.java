package direct.reflect.facilitator.facilitation.session;

import com.redis.testcontainers.RedisContainer;
import direct.reflect.facilitator.auth.AuthService;
import direct.reflect.facilitator.configurator.RetroTemplateRepository;
import direct.reflect.facilitator.configurator.RetroTemplate;
import direct.reflect.facilitator.facilitation.participant.Participant;
import direct.reflect.facilitator.facilitation.participant.ParticipantRepository;
import direct.reflect.facilitator.facilitation.participant.ParticipantRole;
import direct.reflect.facilitator.facilitation.participant.ParticipantStatus;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles({"import", "test"})
@org.springframework.context.annotation.Import(direct.reflect.facilitator.config.TestRedisConfig.class)
@Slf4j
class StepAdvancementApiIntegrationTest {

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
    static RedisContainer redisContainer = new RedisContainer("redis:alpine")
            .withExposedPorts(6379);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RetroSessionRepository sessionRepository;

    @Autowired
    private ParticipantRepository participantRepository;

    @Autowired
    private RetroTemplateRepository templateRepository;

    @MockitoBean
    private AuthService authService;

    private RetroSession testSession;
    private UUID participantUuid;

    private static final Long DEFAULT_TEMPLATE_ID = 1L;

    @BeforeEach
    void setUp() {
        participantUuid = UUID.randomUUID();
        when(authService.getParticipantId(any(HttpServletRequest.class))).thenReturn(participantUuid);

        RetroTemplate template = templateRepository.findById(DEFAULT_TEMPLATE_ID)
                .orElseThrow(() -> new IllegalStateException("Template 1 (Default) not found - is the 'import' profile active?"));

        RetroSession session = new RetroSession();
        session.setName("SSC Advancement Test Session");
        session.setTemplate(template);
        session.setPhase(RetroPhase.DECIDE_ACTIONS);
        testSession = sessionRepository.save(session);

        Participant facilitator = new Participant();
        facilitator.setParticipantId(participantUuid);
        facilitator.setSession(testSession);
        facilitator.setDisplayName("Test Facilitator");
        facilitator.setRole(ParticipantRole.FACILITATOR);
        facilitator.setStatus(ParticipantStatus.ACTIVE);
        participantRepository.save(facilitator);
    }

    @AfterEach
    void cleanUp() {
        participantRepository.deleteAll();
        sessionRepository.delete(testSession);
    }

    @Test
    @WithMockUser(roles = "USER")
    void advanceNext_FromInitialState_MovesToFirstStep() throws Exception {
        RetroSession initial = sessionRepository.findById(testSession.getId()).orElseThrow();
        assertThat(initial.getCurrentStepIndex()).isEqualTo(-1);
        long initialSyncVersion = initial.getSyncVersion();

        mockMvc.perform(post("/api/retros/{retroId}/advance", testSession.getId())
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk());

        RetroSession updated = sessionRepository.findById(testSession.getId()).orElseThrow();
        assertThat(updated.getCurrentStepIndex()).isEqualTo(0);
        assertThat(updated.getSyncVersion()).isGreaterThan(initialSyncVersion);
    }

    @Test
    @WithMockUser(roles = "USER")
    void advanceNext_MultipleTimesSequentially_IncrementsStepIndex() throws Exception {
        for (int expectedStepIndex = 0; expectedStepIndex <= 4; expectedStepIndex++) {
            mockMvc.perform(post("/api/retros/{retroId}/advance", testSession.getId())
                            .with(SecurityMockMvcRequestPostProcessors.csrf()))
                    .andExpect(status().isOk());

            RetroSession updated = sessionRepository.findById(testSession.getId()).orElseThrow();
            assertThat(updated.getCurrentStepIndex())
                    .as("Expected currentStepIndex=%d after %d advance(s)", expectedStepIndex, expectedStepIndex + 1)
                    .isEqualTo(expectedStepIndex);
        }
    }

    @Test
    @WithMockUser(roles = "USER")
    void advanceNext_NonFacilitator_Returns403() throws Exception {
        UUID unknownParticipantId = UUID.randomUUID();
        when(authService.getParticipantId(any(HttpServletRequest.class))).thenReturn(unknownParticipantId);

        mockMvc.perform(post("/api/retros/{retroId}/advance", testSession.getId())
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isForbidden());
    }
}
