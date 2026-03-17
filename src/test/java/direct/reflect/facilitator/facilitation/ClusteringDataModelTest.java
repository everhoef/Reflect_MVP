package direct.reflect.facilitator.facilitation;

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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

@SpringBootTest
@Testcontainers
@ActiveProfiles({"import", "test"})
@Slf4j
class ClusteringDataModelTest {

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
    private ParticipantResponseRepository responseRepository;

    @Autowired
    private RetroSessionRepository sessionRepository;

    @Autowired
    private ParticipantRepository participantRepository;

    @Autowired
    private RetroStepRepository stepRepository;

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
    }

    @AfterEach
    void cleanUp() {
        responseRepository.deleteAll();
        participantRepository.deleteAll();
        sessionRepository.delete(testSession);
    }

    @Test
    void findByRetroStepIdAndClusterId_returnsOnlyResponsesInThatCluster() {
        UUID clusterId = UUID.randomUUID();

        ParticipantResponse clustered1 = buildResponse("note in cluster A");
        clustered1.setClusterId(clusterId);
        clustered1.setClusterName("Cluster A");

        ParticipantResponse clustered2 = buildResponse("another note in cluster A");
        clustered2.setClusterId(clusterId);
        clustered2.setClusterName("Cluster A");

        ParticipantResponse unclustered1 = buildResponse("unclustered note one");
        ParticipantResponse unclustered2 = buildResponse("unclustered note two");

        responseRepository.saveAll(List.of(clustered1, clustered2, unclustered1, unclustered2));

        List<ParticipantResponse> results = responseRepository.findByRetroStepIdAndClusterId(testStep.getId(), clusterId);

        assertThat(results).hasSize(2);
        assertThat(results).extracting("clusterId")
                .containsOnly(clusterId);
    }

    @Test
    void findByRetroStepIdAndClusterIdIsNull_returnsOnlyUnclusteredResponses() {
        UUID clusterId = UUID.randomUUID();

        ParticipantResponse clustered = buildResponse("clustered note");
        clustered.setClusterId(clusterId);
        clustered.setClusterName("Some Cluster");

        ParticipantResponse unclustered1 = buildResponse("unclustered note one");
        ParticipantResponse unclustered2 = buildResponse("unclustered note two");

        responseRepository.saveAll(List.of(clustered, unclustered1, unclustered2));

        List<ParticipantResponse> results = responseRepository.findByRetroStepIdAndClusterIdIsNull(testStep.getId());

        assertThat(results).hasSize(2);
        assertThat(results).extracting("clusterId")
                .containsOnlyNulls();
    }

    @Test
    void saveAndLoad_preservesClusterIdAndClusterName() {
        UUID clusterId = UUID.randomUUID();
        String clusterName = "Communication Issues";

        ParticipantResponse response = buildResponse("daily standups are too long");
        response.setClusterId(clusterId);
        response.setClusterName(clusterName);

        responseRepository.save(response);

        ParticipantResponse loaded = responseRepository.findById(response.getId()).orElseThrow();

        assertThat(loaded.getClusterId()).isEqualTo(clusterId);
        assertThat(loaded.getClusterName()).isEqualTo(clusterName);
    }

    private ParticipantResponse buildResponse(String content) {
        ParticipantResponse response = new ParticipantResponse();
        response.setRetroStep(testStep);
        response.setParticipant(testParticipant);
        response.setResponseData(Map.of("content", content));
        return response;
    }
}
