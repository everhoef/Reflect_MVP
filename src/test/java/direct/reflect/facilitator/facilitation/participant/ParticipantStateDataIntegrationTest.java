package direct.reflect.facilitator.facilitation.participant;

import static org.assertj.core.api.Assertions.assertThat;
import com.redis.testcontainers.RedisContainer;
import direct.reflect.facilitator.auth.AuthService;
import direct.reflect.facilitator.config.TestSecurityOverride;
import direct.reflect.facilitator.facilitation.session.RetroSession;
import direct.reflect.facilitator.facilitation.session.RetroSessionRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles({"import", "test"})
@Import({TestSecurityOverride.class, direct.reflect.facilitator.config.TestRedisConfig.class})
@DisplayName("Participant State Data Integration Tests")
@Slf4j
class ParticipantStateDataIntegrationTest {

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
    private ParticipantRepository participantRepository;

    @Autowired
    private RetroSessionRepository retroSessionRepository;

    @MockitoBean
    private AuthService authService;

    /** Pre-built authentication token injected into each request via org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication() post-processor. */
    private UsernamePasswordAuthenticationToken testAuth;

    @BeforeEach
    void setUp() {
        testAuth = new UsernamePasswordAuthenticationToken(
                "test-user",
                null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }

    @AfterEach
    void cleanUp() {
        participantRepository.deleteAll();
        retroSessionRepository.deleteAll();
    }

    @Test
    void shouldMarkOldParticipantAsLeftWhenUserCreatesNewSession() throws Exception {
        UUID fixedParticipantId = UUID.randomUUID();

        when(authService.getParticipantId(any(HttpServletRequest.class))).thenReturn(fixedParticipantId);
        when(authService.getDisplayName(any(HttpServletRequest.class))).thenReturn("TestUser");

        // ── Step 1: Create first session ──────────────────────────────────────────
        mockMvc.perform(post("/api/retros")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(testAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionName\": \"First Session\"}")
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk());

        List<Participant> afterFirstCreate = participantRepository.findByParticipantId(fixedParticipantId);
        assertThat(afterFirstCreate).hasSize(1);

        Participant firstParticipant = afterFirstCreate.get(0);
        UUID firstSessionId = firstParticipant.getSession().getId();
        long firstSessionSyncVersionAfterCreate = retroSessionRepository.findById(firstSessionId)
                .orElseThrow()
                .getSyncVersion();

        // Business rule 2: new participant starts ACTIVE
        assertThat(firstParticipant.getStatus())
                .as("First session participant should be ACTIVE immediately after creation")
                .isEqualTo(ParticipantStatus.ACTIVE);

        // Business rule 3: participantId matches the mocked identity
        assertThat(firstParticipant.getParticipantId())
                .as("Participant ID should match the mocked identity")
                .isEqualTo(fixedParticipantId);

        // ── Step 2: Create second session (same user) ──────────────────────────────
        mockMvc.perform(post("/api/retros")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(testAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionName\": \"Second Session\"}")
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk());

        // ── Step 3: Assert domain state ────────────────────────────────────────────

        List<Participant> allParticipantsForUser = participantRepository.findByParticipantId(fixedParticipantId);

        // Business rule 6: user has exactly 2 participant records (history preserved, not deleted)
        assertThat(allParticipantsForUser)
                .as("User should have 2 participant records (one per session, history preserved — rule 6)")
                .hasSize(2);

        // Business rule 1: old participant is marked LEFT (not deleted)
        List<Participant> firstSessionParticipants = participantRepository.findBySession_Id(firstSessionId);
        assertThat(firstSessionParticipants).hasSize(1);

        Participant updatedFirstParticipant = firstSessionParticipants.get(0);
        assertThat(updatedFirstParticipant.getStatus())
                .as("Old session participant should be marked LEFT, not deleted (rule 1)")
                .isEqualTo(ParticipantStatus.LEFT);

        // Business rule 5: lastSeen is set when participant is marked LEFT
        assertThat(updatedFirstParticipant.getLastSeen())
                .as("lastSeen should be set when participant is marked LEFT (rule 5)")
                .isNotNull();

        // Business rule 3: same participantId used across both sessions
        assertThat(allParticipantsForUser)
                .as("Same participantId must be used across both sessions — same user identity (rule 3)")
                .allMatch(p -> fixedParticipantId.equals(p.getParticipantId()));

        // Business rule 4: no FK constraint violation — both records exist cleanly (already verified hasSize(2))

        long activeCount = allParticipantsForUser.stream()
                .filter(p -> p.getStatus() == ParticipantStatus.ACTIVE)
                .count();
        long leftCount = allParticipantsForUser.stream()
                .filter(p -> p.getStatus() == ParticipantStatus.LEFT)
                .count();

        // Business rule 7: exactly 1 ACTIVE and 1 LEFT
        assertThat(activeCount)
                .as("Exactly 1 ACTIVE participant record after creating 2 sessions (rule 7)")
                .isEqualTo(1L);

        assertThat(leftCount)
                .as("Exactly 1 LEFT participant record after creating 2 sessions (rule 7)")
                .isEqualTo(1L);

        // Business rule 2: the ACTIVE one is in the second (new) session
        Participant activeParticipant = allParticipantsForUser.stream()
                .filter(p -> p.getStatus() == ParticipantStatus.ACTIVE)
                .findFirst()
                .orElseThrow();

        RetroSession updatedFirstSession = retroSessionRepository.findById(firstSessionId).orElseThrow();
        RetroSession updatedActiveSession = retroSessionRepository.findById(activeParticipant.getSession().getId()).orElseThrow();

        assertThat(activeParticipant.getSession().getId())
                .as("New session participant should be ACTIVE and in the second session (rule 2)")
                .isNotEqualTo(firstSessionId);
        assertThat(updatedFirstSession.getSyncVersion()).isGreaterThan(firstSessionSyncVersionAfterCreate);
        assertThat(updatedActiveSession.getSyncVersion()).isPositive();

        log.debug("All 7 business rules verified: both sessions exist, old=LEFT with lastSeen set, new=ACTIVE, same participantId");
    }
}
