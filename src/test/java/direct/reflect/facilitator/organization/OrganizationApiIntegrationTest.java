package direct.reflect.facilitator.organization;

import com.redis.testcontainers.RedisContainer;
import direct.reflect.facilitator.config.TestRedisConfig;
import direct.reflect.facilitator.config.TestSecurityOverride;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles({"import", "test"})
@Import({TestSecurityOverride.class, TestRedisConfig.class})
class OrganizationApiIntegrationTest {

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
    private OrganizationRepository organizationRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private TeamMemberRepository teamMemberRepository;

    private UsernamePasswordAuthenticationToken managerAuth;
    private UsernamePasswordAuthenticationToken memberAuth;

    @BeforeEach
    void setUp() {
        managerAuth = buildAuthentication("manager-user", "ROLE_MANAGER");
        memberAuth = buildAuthentication("member-user", "ROLE_USER");
    }

    @AfterEach
    void cleanUp() {
        teamMemberRepository.deleteAll();
        teamRepository.deleteAll();
        organizationRepository.deleteAll();
    }

    @Test
    void createOrganization_duplicateSlugReturnsConflict_andGetOrganizationsListsCreatedOrg() throws Exception {
        mockMvc.perform(post("/api/orgs")
                        .with(authentication(managerAuth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Acme Corp","slug":"acme-corp"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("Acme Corp"))
                .andExpect(jsonPath("$.slug").value("acme-corp"));

        mockMvc.perform(post("/api/orgs")
                        .with(authentication(managerAuth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Another Org","slug":"acme-corp"}
                                """))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/orgs")
                        .with(authentication(managerAuth)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].name").value("Acme Corp"))
                .andExpect(jsonPath("$[0].slug").value("acme-corp"));

        assertThat(organizationRepository.findAll()).hasSize(1);
    }

    @Test
    void createTeam_andGetTeamsForOrganization() throws Exception {
        Organization organization = saveOrganization("Acme Corp", "acme-corp");

        mockMvc.perform(post("/api/orgs/{orgId}/teams", organization.getId())
                        .with(authentication(managerAuth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Platform Team"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("Platform Team"))
                .andExpect(jsonPath("$.organizationId").value(organization.getId().toString()));

        mockMvc.perform(get("/api/orgs/{orgId}/teams", organization.getId())
                        .with(authentication(managerAuth)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].name").value("Platform Team"))
                .andExpect(jsonPath("$[0].organizationId").value(organization.getId().toString()));
    }

    @Test
    void addMember_andGetMembersForTeam() throws Exception {
        Organization organization = saveOrganization("Acme Corp", "acme-corp");
        Team team = saveTeam(organization, "Platform Team");
        UUID userId = UUID.randomUUID();

        mockMvc.perform(post("/api/orgs/{orgId}/teams/{teamId}/members", organization.getId(), team.getId())
                        .with(authentication(managerAuth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"%s","role":"MANAGER"}
                                """.formatted(userId)))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.teamId").value(team.getId().toString()))
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.role").value("MANAGER"));

        mockMvc.perform(get("/api/orgs/{orgId}/teams/{teamId}/members", organization.getId(), team.getId())
                        .with(authentication(managerAuth)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].teamId").value(team.getId().toString()))
                .andExpect(jsonPath("$[0].userId").value(userId.toString()))
                .andExpect(jsonPath("$[0].role").value("MANAGER"));

        List<TeamMember> members = teamMemberRepository.findByTeamId(team.getId());
        assertThat(members)
                .singleElement()
                .satisfies(member -> {
                    assertThat(member.getUserId()).isEqualTo(userId);
                    assertThat(member.getRole()).isEqualTo(TeamRole.MANAGER);
                });
    }

    @Test
    void nonManagerGetsForbiddenOnProtectedOperations() throws Exception {
        Organization organization = saveOrganization("Acme Corp", "acme-corp");
        Team team = saveTeam(organization, "Platform Team");

        mockMvc.perform(post("/api/orgs")
                        .with(authentication(memberAuth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Other Org","slug":"other-org"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"error\":\"Access denied\"}"));

        mockMvc.perform(post("/api/orgs/{orgId}/teams", organization.getId())
                        .with(authentication(memberAuth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Delivery Team"}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/orgs/{orgId}/teams/{teamId}/members", organization.getId(), team.getId())
                        .with(authentication(memberAuth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"%s","role":"MEMBER"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isForbidden());
    }

    private Organization saveOrganization(String name, String slug) {
        Organization organization = new Organization();
        organization.setName(name);
        organization.setSlug(slug);
        return organizationRepository.saveAndFlush(organization);
    }

    private Team saveTeam(Organization organization, String teamName) {
        Team team = new Team();
        team.setName(teamName);
        team.setOrganization(organization);
        return teamRepository.saveAndFlush(team);
    }

    private UsernamePasswordAuthenticationToken buildAuthentication(String username, String authority) {
        return new UsernamePasswordAuthenticationToken(
                username,
                null,
                List.of(new SimpleGrantedAuthority(authority))
        );
    }
}
