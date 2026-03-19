package direct.reflect.facilitator.facilitation;

import com.redis.testcontainers.RedisContainer;
import direct.reflect.facilitator.auth.AuthService;
import direct.reflect.facilitator.configurator.RetroStage;
import direct.reflect.facilitator.configurator.RetroStageRepository;
import direct.reflect.facilitator.configurator.RetroStep;
import direct.reflect.facilitator.configurator.RetroStepRepository;
import direct.reflect.facilitator.facilitation.Participant;
import direct.reflect.facilitator.facilitation.ParticipantRepository;
import direct.reflect.facilitator.facilitation.ParticipantRole;
import direct.reflect.facilitator.facilitation.ParticipantStatus;
import direct.reflect.facilitator.facilitation.RetroSession;
import direct.reflect.facilitator.facilitation.RetroSessionRepository;
import direct.reflect.facilitator.facilitation.response.ParticipantResponse;
import direct.reflect.facilitator.facilitation.response.ParticipantResponseRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"import", "test"})
@org.springframework.context.annotation.Import(direct.reflect.facilitator.config.TestRedisConfig.class)
@Slf4j
class VotingIntegrationTest {

    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("postgres")
            .withUsername("postgres")
            .withPassword("postgres");

    @SuppressWarnings("resource")
    static RedisContainer redisContainer = new RedisContainer("redis:alpine")
            .withExposedPorts(6379)
            .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*", 1));

    static {
        postgresContainer.start();
        redisContainer.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgresContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgresContainer::getUsername);
        registry.add("spring.datasource.password", postgresContainer::getPassword);
        registry.add("spring.data.redis.host", redisContainer::getHost);
        registry.add("spring.data.redis.port", () -> redisContainer.getMappedPort(6379));
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ParticipantResponseRepository responseRepository;

    @Autowired
    private RetroSessionRepository sessionRepository;

    @Autowired
    private ParticipantRepository participantRepository;

    @Autowired
    private RetroStepRepository stepRepository;

    @Autowired
    private RetroStageRepository stageRepository;

    @MockitoBean
    private AuthService authService;

    private RetroSession testSession;
    private Participant testParticipant;
    private RetroStep testStep;
    private UUID participantAUuid;

    @BeforeEach
    void setUp() {
        RetroSession session = new RetroSession();
        testSession = sessionRepository.save(session);

        participantAUuid = UUID.randomUUID();

        Participant participant = new Participant();
        participant.setParticipantId(participantAUuid);
        participant.setSession(testSession);
        participant.setDisplayName("Test Voter A");
        participant.setRole(ParticipantRole.FACILITATOR);
        participant.setStatus(ParticipantStatus.ACTIVE);
        testParticipant = participantRepository.save(participant);

        when(authService.getParticipantId(any(HttpServletRequest.class))).thenReturn(participantAUuid);

        RetroStage madSadGladStage = stageRepository.findByMastersheetID(28)
                .orElseThrow(() -> new IllegalStateException("Mad/Sad/Glad stage (mastersheetID=28) not found - check CSV import"));
        testStep = stepRepository.findByRetroStageOrderByOrderIndexAsc(madSadGladStage).stream()
                .filter(s -> s.getOrderIndex() == 3)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Mad/Sad/Glad voting step (orderIndex=3) not found in stage 28"));
    }

    @AfterEach
    void cleanUp() {
        responseRepository.deleteAll();
        participantRepository.deleteAll();
        sessionRepository.delete(testSession);
    }

    private ParticipantResponse buildResponse(String content) {
        ParticipantResponse response = new ParticipantResponse();
        response.setRetroStep(testStep);
        response.setParticipant(testParticipant);
        response.setResponseData(new java.util.HashMap<>(Map.of("content", content, "columnId", "start")));
        return response;
    }

    @SuppressWarnings("unchecked")
    private List<String> getVotes(ParticipantResponse response) {
        Object votesObj = response.getResponseData().get("votes");
        if (votesObj instanceof List<?>) {
            return (List<String>) votesObj;
        }
        return List.of();
    }

    @Test
    @WithMockUser(roles = "USER")
    void vote_OnResponse_IncrementsVoteCount() throws Exception {
        ParticipantResponse response = responseRepository.save(buildResponse("slow deploys"));

        mockMvc.perform(post("/api/retro/{retroId}/response/{responseId}/vote",
                        testSession.getId(), response.getId())
                        .with(csrf()))
                .andExpect(status().isOk());

        ParticipantResponse loaded = responseRepository.findById(response.getId()).orElseThrow();
        List<String> votes = getVotes(loaded);
        assertThat(votes).hasSize(1);
        assertThat(votes).contains(participantAUuid.toString());
    }

    @Test
    @WithMockUser(roles = "USER")
    void vote_SameResponseTwice_UnvotesAndDecrementsCount() throws Exception {
        ParticipantResponse response = responseRepository.save(buildResponse("too many meetings"));

        mockMvc.perform(post("/api/retro/{retroId}/response/{responseId}/vote",
                        testSession.getId(), response.getId())
                        .with(csrf()))
                .andExpect(status().isOk());

        assertThat(getVotes(responseRepository.findById(response.getId()).orElseThrow())).hasSize(1);

        mockMvc.perform(post("/api/retro/{retroId}/response/{responseId}/vote",
                        testSession.getId(), response.getId())
                        .with(csrf()))
                .andExpect(status().isOk());

        assertThat(getVotes(responseRepository.findById(response.getId()).orElseThrow())).isEmpty();
    }

    @Test
    @WithMockUser(roles = "USER")
    void vote_MultipleResponses_CountsCorrectly() throws Exception {
        ParticipantResponse r1 = responseRepository.save(buildResponse("item one"));
        ParticipantResponse r2 = responseRepository.save(buildResponse("item two"));
        ParticipantResponse r3 = responseRepository.save(buildResponse("item three"));

        for (UUID responseId : List.of(r1.getId(), r2.getId(), r3.getId())) {
            mockMvc.perform(post("/api/retro/{retroId}/response/{responseId}/vote",
                            testSession.getId(), responseId)
                            .with(csrf()))
                    .andExpect(status().isOk());
        }

        assertThat(getVotes(responseRepository.findById(r1.getId()).orElseThrow())).hasSize(1);
        assertThat(getVotes(responseRepository.findById(r2.getId()).orElseThrow())).hasSize(1);
        assertThat(getVotes(responseRepository.findById(r3.getId()).orElseThrow())).hasSize(1);
    }

    @Test
    @WithMockUser(roles = "USER")
    void vote_ExceedsVoteLimit_Returns400() throws Exception {
        List<ParticipantResponse> responses = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            responses.add(responseRepository.save(buildResponse("item " + i)));
        }

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/retro/{retroId}/response/{responseId}/vote",
                            testSession.getId(), responses.get(i).getId())
                            .with(csrf()))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(post("/api/retro/{retroId}/response/{responseId}/vote",
                        testSession.getId(), responses.get(5).getId())
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "USER")
    void vote_TwoParticipants_VotesAreIndependent() throws Exception {
        UUID participantBUuid = UUID.randomUUID();
        Participant participantB = new Participant();
        participantB.setParticipantId(participantBUuid);
        participantB.setSession(testSession);
        participantB.setDisplayName("Voter B");
        participantB.setRole(ParticipantRole.PARTICIPANT);
        participantB.setStatus(ParticipantStatus.ACTIVE);
        participantRepository.save(participantB);

        ParticipantResponse response = responseRepository.save(buildResponse("shared concern"));

        when(authService.getParticipantId(any(HttpServletRequest.class))).thenReturn(participantAUuid);
        mockMvc.perform(post("/api/retro/{retroId}/response/{responseId}/vote",
                        testSession.getId(), response.getId())
                        .with(csrf()))
                .andExpect(status().isOk());

        assertThat(getVotes(responseRepository.findById(response.getId()).orElseThrow())).hasSize(1);

        when(authService.getParticipantId(any(HttpServletRequest.class))).thenReturn(participantBUuid);
        mockMvc.perform(post("/api/retro/{retroId}/response/{responseId}/vote",
                        testSession.getId(), response.getId())
                        .with(csrf()))
                .andExpect(status().isOk());

        assertThat(getVotes(responseRepository.findById(response.getId()).orElseThrow())).hasSize(2);

        when(authService.getParticipantId(any(HttpServletRequest.class))).thenReturn(participantAUuid);
        mockMvc.perform(post("/api/retro/{retroId}/response/{responseId}/vote",
                        testSession.getId(), response.getId())
                        .with(csrf()))
                .andExpect(status().isOk());

        List<String> finalVotes = getVotes(responseRepository.findById(response.getId()).orElseThrow());
        assertThat(finalVotes).hasSize(1);
        assertThat(finalVotes).contains(participantBUuid.toString());
        assertThat(finalVotes).doesNotContain(participantAUuid.toString());
    }
}
