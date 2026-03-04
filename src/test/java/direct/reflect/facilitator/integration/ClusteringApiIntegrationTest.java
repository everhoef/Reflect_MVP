package direct.reflect.facilitator.integration;

import com.redis.testcontainers.RedisContainer;
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
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import direct.reflect.facilitator.facilitation.ParticipantService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles({"import", "test"})
@Slf4j
class ClusteringApiIntegrationTest {

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
    private ParticipantResponseRepository responseRepository;

    @Autowired
    private RetroSessionRepository sessionRepository;

    @Autowired
    private ParticipantRepository participantRepository;

    @Autowired
    private RetroStepRepository stepRepository;

    @MockitoBean
    private ParticipantService participantService;

    private RetroSession testSession;
    private Participant testParticipant;
    private RetroStep testStep;

    @BeforeEach
    void setUp() {
        RetroSession session = new RetroSession();
        testSession = sessionRepository.save(session);

        Participant participant = new Participant();
        participant.setParticipantId(UUID.randomUUID());
        participant.setSession(testSession);
        participant.setDisplayName("Test User");
        participant.setRole(ParticipantRole.FACILITATOR);
        participant.setStatus(ParticipantStatus.ACTIVE);
        testParticipant = participantRepository.save(participant);

        testStep = stepRepository.findAll().get(0);
        when(participantService.canAccessRetro(any(UUID.class))).thenReturn(true);
    }

    @AfterEach
    void cleanUp() {
        responseRepository.deleteAll();
        participantRepository.deleteAll();
        sessionRepository.delete(testSession);
    }

    @Test
    @WithMockUser(roles = "USER")
    void mergeResponses_ValidRequest_ReturnsMergedClusterId() throws Exception {
        // Given: two unclustered responses
        ParticipantResponse r1 = buildResponse("daily standups are too long");
        ParticipantResponse r2 = buildResponse("meetings never have an agenda");
        responseRepository.saveAll(List.of(r1, r2));

        String mergeBody = "{\"responseIds\": [\"" + r1.getId() + "\", \"" + r2.getId() + "\"]}";

        // When: POST merge
        String responseJson = mockMvc.perform(post("/api/retro/{retroId}/step/{stepId}/cluster/merge",
                        testSession.getId(), testStep.getId())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mergeBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clusterId").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        // Then: both responses share the same non-null clusterId
        // Parse clusterId from JSON response: {"clusterId":"<uuid>"}
        UUID clusterId = UUID.fromString(responseJson.replaceAll(".*\"clusterId\":\"([^\"]+)\".*", "$1"));

        ParticipantResponse loaded1 = responseRepository.findById(r1.getId()).orElseThrow();
        ParticipantResponse loaded2 = responseRepository.findById(r2.getId()).orElseThrow();
        assertThat(loaded1.getClusterId()).isEqualTo(clusterId);
        assertThat(loaded2.getClusterId()).isEqualTo(clusterId);
    }

    @Test
    @WithMockUser(roles = "USER")
    void unmergeResponse_ValidRequest_ClearsCluster() throws Exception {
        // Given: a response already in a cluster
        UUID clusterId = UUID.randomUUID();
        ParticipantResponse r1 = buildResponse("too many interruptions");
        r1.setClusterId(clusterId);
        r1.setClusterName("Communication");
        ParticipantResponse r2 = buildResponse("unclear requirements");
        r2.setClusterId(clusterId);
        r2.setClusterName("Communication");
        responseRepository.saveAll(List.of(r1, r2));

        String unmergeBody = "{\"responseId\": \"" + r1.getId() + "\"}";

        mockMvc.perform(post("/api/retro/{retroId}/step/{stepId}/cluster/unmerge",
                        testSession.getId(), testStep.getId())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(unmergeBody))
                .andExpect(status().isOk());

        // Then: r1 has no cluster, r2 still has cluster
        ParticipantResponse loaded1 = responseRepository.findById(r1.getId()).orElseThrow();
        ParticipantResponse loaded2 = responseRepository.findById(r2.getId()).orElseThrow();
        assertThat(loaded1.getClusterId()).isNull();
        assertThat(loaded1.getClusterName()).isNull();
        assertThat(loaded2.getClusterId()).isEqualTo(clusterId);
    }

    @Test
    @WithMockUser(roles = "USER")
    void renameCluster_ValidRequest_UpdatesAllResponsesInCluster() throws Exception {
        // Given: two responses in the same cluster
        UUID clusterId = UUID.randomUUID();
        ParticipantResponse r1 = buildResponse("no code reviews");
        r1.setClusterId(clusterId);
        r1.setClusterName("Old Name");
        ParticipantResponse r2 = buildResponse("PRs take too long");
        r2.setClusterId(clusterId);
        r2.setClusterName("Old Name");
        responseRepository.saveAll(List.of(r1, r2));

        String renameBody = "{\"name\": \"Code Review Issues\"}";

        mockMvc.perform(put("/api/retro/{retroId}/step/{stepId}/cluster/{clusterId}/name",
                        testSession.getId(), testStep.getId(), clusterId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(renameBody))
                .andExpect(status().isOk());

        // Then: both responses have updated cluster name
        ParticipantResponse loaded1 = responseRepository.findById(r1.getId()).orElseThrow();
        ParticipantResponse loaded2 = responseRepository.findById(r2.getId()).orElseThrow();
        assertThat(loaded1.getClusterName()).isEqualTo("Code Review Issues");
        assertThat(loaded2.getClusterName()).isEqualTo("Code Review Issues");
    }

    @Test
    @WithMockUser(roles = "USER")
    void getClusters_ReturnsClustersAndUnclustered() throws Exception {
        // Given: mixed clustered and unclustered responses
        UUID clusterId = UUID.randomUUID();
        ParticipantResponse clustered1 = buildResponse("note in cluster");
        clustered1.setClusterId(clusterId);
        clustered1.setClusterName("My Cluster");
        ParticipantResponse clustered2 = buildResponse("another note in cluster");
        clustered2.setClusterId(clusterId);
        clustered2.setClusterName("My Cluster");
        ParticipantResponse unclustered = buildResponse("standalone note");
        responseRepository.saveAll(List.of(clustered1, clustered2, unclustered));

        // When: GET clusters
        mockMvc.perform(get("/api/retro/{retroId}/step/{stepId}/clusters",
                        testSession.getId(), testStep.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clustered").isMap())
                .andExpect(jsonPath("$.unclustered").isArray())
                .andExpect(jsonPath("$.unclustered.length()").value(1))
                .andExpect(jsonPath("$.clustered." + clusterId).isArray())
                .andExpect(jsonPath("$.clustered." + clusterId + ".length()").value(2));
    }

    private ParticipantResponse buildResponse(String content) {
        ParticipantResponse response = new ParticipantResponse();
        response.setRetroStep(testStep);
        response.setParticipant(testParticipant);
        response.setResponseData(Map.of("content", content, "columnId", "mad"));
        return response;
    }
}
