package direct.reflect.facilitator.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.redis.testcontainers.RedisContainer;
import direct.reflect.facilitator.config.TestRedisConfig;
import direct.reflect.facilitator.facilitation.RetroSession;
import direct.reflect.facilitator.facilitation.RetroSessionRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
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
class OrganizationDataModelTest {

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
    private OrganizationRepository organizationRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private TeamMemberRepository teamMemberRepository;

    @Autowired
    private RetroSessionRepository sessionRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        teamMemberRepository.deleteAll();
        sessionRepository.deleteAll();
        teamRepository.deleteAll();
        organizationRepository.deleteAll();
    }

    @Test
    void saveAndLoad_persistsOrganizationAndTeamRelationships() {
        Organization organization = new Organization();
        organization.setName("Acme Corp");
        organization.setSlug("acme-corp");
        organizationRepository.saveAndFlush(organization);

        Team team = new Team();
        team.setName("Platform");
        team.setOrganization(organization);
        teamRepository.saveAndFlush(team);

        entityManager.clear();

        Organization loadedOrganization = organizationRepository.findBySlug("acme-corp").orElseThrow();
        List<Team> loadedTeams = teamRepository.findByOrganization_Id(loadedOrganization.getId());

        assertThat(loadedOrganization.getId()).isNotNull();
        assertThat(loadedOrganization.getName()).isEqualTo("Acme Corp");
        assertThat(loadedOrganization.getSlug()).isEqualTo("acme-corp");
        assertThat(loadedTeams)
                .singleElement()
                .satisfies(loadedTeam -> {
                    assertThat(loadedTeam.getName()).isEqualTo("Platform");
                    assertThat(loadedTeam.getOrganization().getId()).isEqualTo(loadedOrganization.getId());
                });
    }

    @Test
    void teamMemberCompositeKey_persistsRoleAndRejectsDuplicates() {
        Organization organization = saveOrganization("Acme Corp", "acme-corp");
        Team team = saveTeam(organization, "Platform");
        UUID userId = UUID.randomUUID();

        TeamMember member = new TeamMember();
        member.setTeam(team);
        member.setUserId(userId);
        member.setRole(TeamRole.MANAGER);
        teamMemberRepository.saveAndFlush(member);

        entityManager.clear();

        List<TeamMember> members = teamMemberRepository.findByTeamId(team.getId());

        assertThat(members)
                .singleElement()
                .satisfies(savedMember -> {
                    assertThat(savedMember.getUserId()).isEqualTo(userId);
                    assertThat(savedMember.getRole()).isEqualTo(TeamRole.MANAGER);
                    assertThat(savedMember.getTeam().getId()).isEqualTo(team.getId());
                });

        assertThatThrownBy(() -> jdbcTemplate.update(
                "insert into team_members (team_id, user_id, role) values (?, ?, ?)",
                team.getId(),
                userId,
                TeamRole.MEMBER.name()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void retroSession_allowsNullAndNonNullTeamAssociations() {
        RetroSession sessionWithoutTeam = new RetroSession();
        sessionWithoutTeam.setName("Company Retro");
        sessionRepository.saveAndFlush(sessionWithoutTeam);

        Organization organization = saveOrganization("Beta Corp", "beta-corp");
        Team team = saveTeam(organization, "Delivery");

        RetroSession sessionWithTeam = new RetroSession();
        sessionWithTeam.setName("Delivery Retro");
        sessionWithTeam.setTeam(team);
        sessionRepository.saveAndFlush(sessionWithTeam);

        entityManager.clear();

        RetroSession loadedWithoutTeam = sessionRepository.findById(sessionWithoutTeam.getId()).orElseThrow();
        RetroSession loadedWithTeam = sessionRepository.findById(sessionWithTeam.getId()).orElseThrow();

        assertThat(loadedWithoutTeam.getTeam()).isNull();
        assertThat(loadedWithTeam.getTeam()).isNotNull();
        assertThat(loadedWithTeam.getTeam().getId()).isEqualTo(team.getId());
    }

    private Organization saveOrganization(String name, String slug) {
        Organization organization = new Organization();
        organization.setName(name);
        organization.setSlug(slug);
        return organizationRepository.saveAndFlush(organization);
    }

    private Team saveTeam(Organization organization, String name) {
        Team team = new Team();
        team.setName(name);
        team.setOrganization(organization);
        return teamRepository.saveAndFlush(team);
    }
}
