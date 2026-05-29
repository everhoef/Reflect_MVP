package direct.reflect.facilitator.facilitation.escalation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.redis.testcontainers.RedisContainer;
import direct.reflect.facilitator.config.TestRedisConfig;
import direct.reflect.facilitator.facilitation.escalation.domain.EscalationThresholdPolicy;
import direct.reflect.facilitator.facilitation.participant.ParticipantService;
import direct.reflect.facilitator.facilitation.participant.SseParticipantAccess;
import direct.reflect.facilitator.facilitation.session.RetroSession;
import direct.reflect.facilitator.facilitation.session.RetroSessionRepository;
import direct.reflect.facilitator.facilitation.session.TeamBackedRetroFixture;
import direct.reflect.facilitator.organization.OrganizationRepository;
import direct.reflect.facilitator.organization.TeamRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.ManyToOne;
import jakarta.validation.ConstraintViolationException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
@ActiveProfiles({"import", "test"})
@Import(TestRedisConfig.class)
class EscalationDataModelTest {

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
    private EscalatedItemRepository escalatedItemRepository;

    @Autowired
    private EscalatedItemVoteRepository escalatedItemVoteRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private RetroSessionRepository sessionRepository;

    @Autowired
    private TeamBackedRetroFixture teamBackedRetroFixture;

    @Autowired
    private EntityManager entityManager;

    @AfterEach
    void cleanUp() {
        escalatedItemVoteRepository.deleteAll();
        escalatedItemRepository.deleteAll();
        sessionRepository.deleteAll();
        teamRepository.deleteAll();
        organizationRepository.deleteAll();
    }

    @Test
    void saveAndFlush_persistsEscalationThresholdAndSessionTeamLinkage() {
        RetroSession retroSession = teamBackedRetroFixture.createTeamBackedSession("Platform");
        UUID teamId = retroSession.getTeamId();

        EscalatedItem escalatedItem = escalatedItemRepository.saveAndFlush(buildEscalatedItem(
                retroSession,
                teamId,
                "Cross-team API dependency is blocking releases",
                EscalationThresholdPolicy.calculateVoteThreshold(5),
                item -> {}));
        entityManager.clear();

        EscalatedItem loadedEscalatedItem = escalatedItemRepository.findById(escalatedItem.getId()).orElseThrow();

        assertThat(loadedEscalatedItem.getId()).isNotNull();
        assertThat(loadedEscalatedItem.getProblemDescription())
                .isEqualTo("Cross-team API dependency is blocking releases");
        assertThat(loadedEscalatedItem.getRetroSession().getId()).isEqualTo(retroSession.getId());
        assertThat(loadedEscalatedItem.getTeamId()).isEqualTo(teamId);
        assertThat(loadedEscalatedItem.getVoteThreshold()).isEqualTo(3);
        assertThat(loadedEscalatedItem.getCreatedAt()).isNotNull();
    }

    @Test
    void saveAndFlush_rejectsInvalidEscalationContracts() {
        RetroSession retroSession = teamBackedRetroFixture.createTeamBackedSession("Delivery");
        UUID teamId = retroSession.getTeamId();

        assertInvalid(buildEscalatedItem(retroSession, teamId, null, 2, item -> {}));
        assertInvalid(buildEscalatedItem(retroSession, teamId, "   ", 2, item -> {}));
        assertInvalid(buildEscalatedItem(retroSession, teamId, "Manager support needed", 0, item -> {}));
        assertInvalid(buildEscalatedItem(retroSession, teamId, "Manager support needed", 2, item -> item.setTeamId(null)));
        assertInvalid(buildEscalatedItem(retroSession, teamId, "Manager support needed", 2, item -> item.setRetroSession(null)));
    }

    @Test
    void thresholdPolicy_calculatesSimpleMajority() {
        assertThat(EscalationThresholdPolicy.calculateVoteThreshold(1)).isEqualTo(1);
        assertThat(EscalationThresholdPolicy.calculateVoteThreshold(2)).isEqualTo(2);
        assertThat(EscalationThresholdPolicy.calculateVoteThreshold(3)).isEqualTo(2);
        assertThat(EscalationThresholdPolicy.calculateVoteThreshold(4)).isEqualTo(3);
        assertThat(EscalationThresholdPolicy.calculateVoteThreshold(5)).isEqualTo(3);

        assertThatThrownBy(() -> EscalationThresholdPolicy.calculateVoteThreshold(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Participant count must be at least 1");
    }

    @Test
    void voteRepository_scopesLookupAndCountsByEscalatedItemCompositeKey() {
        RetroSession retroSession = teamBackedRetroFixture.createTeamBackedSession("Enablement");
        UUID teamId = retroSession.getTeamId();
        UUID participantId = UUID.randomUUID();
        UUID otherParticipantId = UUID.randomUUID();

        EscalatedItem firstEscalatedItem = escalatedItemRepository.saveAndFlush(buildEscalatedItem(
                retroSession,
                teamId,
                "Shared deployment process needs manager support",
                EscalationThresholdPolicy.calculateVoteThreshold(2),
                item -> {}));
        EscalatedItem secondEscalatedItem = escalatedItemRepository.saveAndFlush(buildEscalatedItem(
                retroSession,
                teamId,
                "Cross-team release coordination needs escalation",
                EscalationThresholdPolicy.calculateVoteThreshold(2),
                item -> {}));

        escalatedItemVoteRepository.saveAndFlush(buildVote(firstEscalatedItem, participantId));
        escalatedItemVoteRepository.saveAndFlush(buildVote(firstEscalatedItem, otherParticipantId));
        escalatedItemVoteRepository.saveAndFlush(buildVote(secondEscalatedItem, participantId));
        entityManager.clear();

        assertThat(escalatedItemVoteRepository.countByEscalatedItemId(firstEscalatedItem.getId())).isEqualTo(2);
        assertThat(escalatedItemVoteRepository.countByEscalatedItemId(secondEscalatedItem.getId())).isEqualTo(1);

        Optional<EscalatedItemVote> matchingVote = escalatedItemVoteRepository.findByEscalatedItemIdAndParticipantId(
                firstEscalatedItem.getId(), participantId);

        assertThat(matchingVote).isPresent();
        assertThat(matchingVote.orElseThrow().getEscalatedItemId()).isEqualTo(firstEscalatedItem.getId());
        assertThat(matchingVote.orElseThrow().getParticipantId()).isEqualTo(participantId);
        assertThat(matchingVote.orElseThrow().getVotedAt()).isNotNull();
        assertThat(escalatedItemVoteRepository.findByEscalatedItemIdAndParticipantId(firstEscalatedItem.getId(), UUID.randomUUID()))
                .isEmpty();
    }

    private EscalatedItem buildEscalatedItem(
            RetroSession retroSession,
            UUID teamId,
            String problemDescription,
            int voteThreshold,
            Consumer<EscalatedItem> customizer) {
        EscalatedItem escalatedItem = new EscalatedItem();
        escalatedItem.setRetroSession(retroSession);
        escalatedItem.setTeamId(teamId);
        escalatedItem.setProblemDescription(problemDescription);
        escalatedItem.setVoteThreshold(voteThreshold);
        customizer.accept(escalatedItem);
        return escalatedItem;
    }

    private EscalatedItemVote buildVote(EscalatedItem escalatedItem, UUID participantId) {
        EscalatedItemVote vote = new EscalatedItemVote();
        vote.setId(new EscalatedItemVoteId(escalatedItem.getId(), participantId));
        vote.setEscalatedItem(escalatedItem);
        return vote;
    }

    private void assertInvalid(EscalatedItem escalatedItem) {
        assertThatThrownBy(() -> escalatedItemRepository.saveAndFlush(escalatedItem))
                .isInstanceOfAny(ConstraintViolationException.class, DataIntegrityViolationException.class);
    }

    /**
     * PR #20 architectural keep r3160764595:
     * EscalatedItem.teamId is intentionally a UUID scalar, NOT a {@literal @}ManyToOne Team entity relationship.
     * This keeps the escalation module decoupled from the organization module's entity lifecycle and avoids
     * cascading persistence concerns. The team identity is only needed for filtering/lookup, not for navigation.
     */
    @Test
    void teamId_isUuidScalar_notManyToOneEntityRelationship() throws NoSuchFieldException {
        java.lang.reflect.Field teamIdField = EscalatedItem.class.getDeclaredField("teamId");

        assertThat(teamIdField.getType())
                .as("teamId must be a UUID scalar, not a Team entity")
                .isEqualTo(UUID.class);

        assertThat(teamIdField.getAnnotation(ManyToOne.class))
                .as("teamId must NOT be annotated with @ManyToOne (intentional scalar design)")
                .isNull();

        assertThat(teamIdField.getAnnotation(jakarta.persistence.Column.class))
                .as("teamId must have @Column (simple scalar mapping)")
                .isNotNull();
    }

    /**
     * PR #20 architectural keep r3160765078:
     * SseParticipantAccess is an intentionally narrow interface owned by the facilitation module.
     * It exposes exactly one operation (authorizeSseConnection) and one record (SseParticipantConnection),
     * making it a minimal seam between eventing and participant concerns. ParticipantService implements it.
     */
    @Test
    void sseParticipantAccess_isIntentionallyNarrowInterface() {
        Method[] declaredMethods = SseParticipantAccess.class.getDeclaredMethods();

        long abstractMethodCount = Arrays.stream(declaredMethods)
                .filter(m -> java.lang.reflect.Modifier.isAbstract(m.getModifiers()))
                .count();
        assertThat(abstractMethodCount)
                .as("SseParticipantAccess must expose exactly one abstract method (narrow seam)")
                .isEqualTo(1);

        assertThat(declaredMethods)
                .as("SseParticipantAccess must declare authorizeSseConnection")
                .anyMatch(m -> m.getName().equals("authorizeSseConnection"));

        Class<?>[] declaredClasses = SseParticipantAccess.class.getDeclaredClasses();
        assertThat(declaredClasses)
                .as("SseParticipantAccess must contain exactly one nested type (the record)")
                .hasSize(1);
        assertThat(declaredClasses[0])
                .as("The nested type must be the SseParticipantConnection record")
                .isEqualTo(SseParticipantAccess.SseParticipantConnection.class);

        assertThat(SseParticipantAccess.class)
                .as("ParticipantService must implement SseParticipantAccess")
                .isAssignableFrom(ParticipantService.class);
    }
}
