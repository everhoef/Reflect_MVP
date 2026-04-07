package direct.reflect.facilitator.facilitation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.redis.testcontainers.RedisContainer;
import direct.reflect.facilitator.config.TestRedisConfig;
import direct.reflect.facilitator.facilitation.escalation.EscalatedItem;
import direct.reflect.facilitator.facilitation.escalation.EscalatedItemRepository;
import direct.reflect.facilitator.facilitation.escalation.EscalatedItemVote;
import direct.reflect.facilitator.facilitation.escalation.EscalatedItemVoteId;
import direct.reflect.facilitator.facilitation.escalation.EscalatedItemVoteRepository;
import direct.reflect.facilitator.organization.Organization;
import direct.reflect.facilitator.organization.OrganizationRepository;
import direct.reflect.facilitator.organization.Team;
import direct.reflect.facilitator.organization.TeamRepository;
import jakarta.persistence.EntityManager;
import jakarta.validation.ConstraintViolationException;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        escalatedItemVoteRepository.deleteAll();
        escalatedItemRepository.deleteAll();
        sessionRepository.deleteAll();
        teamRepository.deleteAll();
        organizationRepository.deleteAll();
    }

    @Test
    void saveAndLoad_persistsEscalatedItemWithThresholdAndLinkage() {
        Team team = saveTeam("Acme Corp", "acme-corp", "Platform");
        RetroSession retroSession = saveSession("Platform Retro", team);

        EscalatedItem escalatedItem = buildEscalatedItem(
                retroSession,
                team,
                "Cross-team API dependency is blocking releases",
                EscalatedItem.calculateVoteThreshold(5));

        escalatedItemRepository.saveAndFlush(escalatedItem);
        entityManager.clear();

        EscalatedItem loadedEscalatedItem = escalatedItemRepository.findById(escalatedItem.getId()).orElseThrow();

        assertThat(loadedEscalatedItem.getId()).isNotNull();
        assertThat(loadedEscalatedItem.getProblemDescription())
                .isEqualTo("Cross-team API dependency is blocking releases");
        assertThat(loadedEscalatedItem.getRetroSession().getId()).isEqualTo(retroSession.getId());
        assertThat(loadedEscalatedItem.getTeam().getId()).isEqualTo(team.getId());
        assertThat(loadedEscalatedItem.getVoteThreshold()).isEqualTo(3);
        assertThat(loadedEscalatedItem.getCreatedAt()).isNotNull();
    }

    @Test
    void saveAndFlush_rejectsMissingProblemDescription() {
        Team team = saveTeam("Beta Corp", "beta-corp", "Delivery");
        RetroSession retroSession = saveSession("Delivery Retro", team);

        EscalatedItem missingDescription = buildEscalatedItem(retroSession, team, null, 2);
        EscalatedItem blankDescription = buildEscalatedItem(retroSession, team, "   ", 2);

        assertInvalid(missingDescription);
        assertInvalid(blankDescription);
    }

    @Test
    void calculateVoteThreshold_returnsSimpleMajority() {
        assertThat(EscalatedItem.calculateVoteThreshold(1)).isEqualTo(1);
        assertThat(EscalatedItem.calculateVoteThreshold(2)).isEqualTo(2);
        assertThat(EscalatedItem.calculateVoteThreshold(3)).isEqualTo(2);
        assertThat(EscalatedItem.calculateVoteThreshold(4)).isEqualTo(3);
        assertThat(EscalatedItem.calculateVoteThreshold(5)).isEqualTo(3);

        assertThatThrownBy(() -> EscalatedItem.calculateVoteThreshold(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Participant count must be at least 1");
    }

    @Test
    void voteRepository_supportsToggleFriendlyLookupCountingAndCompositeKey() {
        Team team = saveTeam("Gamma Corp", "gamma-corp", "Enablement");
        RetroSession retroSession = saveSession("Enablement Retro", team);
        UUID participantOneId = UUID.randomUUID();
        UUID participantTwoId = UUID.randomUUID();

        EscalatedItem escalatedItem = escalatedItemRepository.saveAndFlush(buildEscalatedItem(
                retroSession,
                team,
                "Shared deployment process needs manager support",
                EscalatedItem.calculateVoteThreshold(2)));

        EscalatedItemVote firstVote = buildVote(escalatedItem, participantOneId);
        escalatedItemVoteRepository.saveAndFlush(firstVote);
        entityManager.clear();

        assertThat(escalatedItemVoteRepository.countByEscalatedItemId(escalatedItem.getId())).isEqualTo(1);

        Optional<EscalatedItemVote> existingVote = escalatedItemVoteRepository.findByEscalatedItemIdAndParticipantId(
                escalatedItem.getId(), participantOneId);

        assertThat(existingVote).isPresent();
        assertThat(existingVote.orElseThrow().getVotedAt()).isNotNull();

        escalatedItemVoteRepository.delete(existingVote.orElseThrow());
        escalatedItemVoteRepository.flush();
        entityManager.clear();

        assertThat(escalatedItemVoteRepository.countByEscalatedItemId(escalatedItem.getId())).isZero();
        assertThat(escalatedItemVoteRepository.findByEscalatedItemIdAndParticipantId(
                        escalatedItem.getId(), participantOneId))
                .isEmpty();

        EscalatedItemVote secondVote = buildVote(escalatedItem, participantTwoId);
        escalatedItemVoteRepository.saveAndFlush(secondVote);
        entityManager.clear();

        assertThat(escalatedItemVoteRepository.countByEscalatedItemId(escalatedItem.getId())).isEqualTo(1);

        assertThatThrownBy(() -> jdbcTemplate.update(
                        "insert into escalated_item_votes (escalated_item_id, participant_id, voted_at) values (?, ?, ?)",
                        escalatedItem.getId(),
                        participantTwoId,
                        LocalDateTime.now()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Team saveTeam(String organizationName, String organizationSlug, String teamName) {
        Organization organization = new Organization();
        organization.setName(organizationName);
        organization.setSlug(organizationSlug);
        organizationRepository.saveAndFlush(organization);

        Team team = new Team();
        team.setName(teamName);
        team.setOrganization(organization);
        return teamRepository.saveAndFlush(team);
    }

    private RetroSession saveSession(String name, Team team) {
        RetroSession retroSession = new RetroSession();
        retroSession.setName(name);
        retroSession.setTeam(team);
        return sessionRepository.saveAndFlush(retroSession);
    }

    private EscalatedItem buildEscalatedItem(
            RetroSession retroSession, Team team, String problemDescription, int voteThreshold) {
        EscalatedItem escalatedItem = new EscalatedItem();
        escalatedItem.setRetroSession(retroSession);
        escalatedItem.setTeam(team);
        escalatedItem.setProblemDescription(problemDescription);
        escalatedItem.setVoteThreshold(voteThreshold);
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
}
