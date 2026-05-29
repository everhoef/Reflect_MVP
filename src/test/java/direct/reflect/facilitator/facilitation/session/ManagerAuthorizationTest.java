package direct.reflect.facilitator.facilitation.session;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.redis.testcontainers.RedisContainer;
import direct.reflect.facilitator.auth.AuthService;
import direct.reflect.facilitator.auth.infrastructure.security.SecurityConfig;
import direct.reflect.facilitator.config.TestRedisConfig;
import direct.reflect.facilitator.config.TestSecurityOverride;
import direct.reflect.facilitator.organization.Organization;
import direct.reflect.facilitator.organization.OrganizationRepository;
import direct.reflect.facilitator.organization.TeamMember;
import direct.reflect.facilitator.organization.TeamMemberRepository;
import direct.reflect.facilitator.organization.Team;
import direct.reflect.facilitator.organization.TeamRepository;
import direct.reflect.facilitator.organization.TeamRole;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles({"import", "test"})
@Import({
    TestSecurityOverride.class,
    TestRedisConfig.class,
    ManagerAuthorizationTest.ManagerAuthorizationEndpointConfig.class
})
class ManagerAuthorizationTest {

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
    private AuthService authService;

    @Autowired
    private SecurityConfig.OidcSuccessHandler oidcSuccessHandler;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private TeamMemberRepository teamMemberRepository;

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        teamMemberRepository.deleteAll();
        teamRepository.deleteAll();
        organizationRepository.deleteAll();
    }

    @Test
    void oidcManagerGetsRoleManagerAndCanAccessManagerEndpoint() throws Exception {
        Team team = saveTeam("Managers");
        saveTeamMember(team, "manager-user", TeamRole.MANAGER);

        Authentication authentication = authenticateOidcUser("manager-user", "Manager User", "manager@example.com");

        assertThat(authorityNames(authentication.getAuthorities()))
            .contains("ROLE_USER", "ROLE_MANAGER")
            .doesNotContain("ROLE_GUEST");

        mockMvc.perform(get("/api/manager/test-probe").with(authentication(authentication)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void nonManagerGetsForbiddenOnManagerEndpoint() throws Exception {
        Team team = saveTeam("Members");
        saveTeamMember(team, "member-user", TeamRole.MEMBER);

        Authentication authentication = authenticateOidcUser("member-user", "Member User", "member@example.com");

        assertThat(authorityNames(authentication.getAuthorities()))
            .contains("ROLE_USER")
            .doesNotContain("ROLE_MANAGER");

        mockMvc.perform(get("/api/manager/test-probe").with(authentication(authentication)))
            .andExpect(status().isForbidden())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().json("{\"error\":\"Access denied\"}"));
    }

    @Test
    void guestUserNeverGetsRoleManager() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        authService.initializeGuestSession(request, "Guest User");

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        assertThat(authorityNames(authentication.getAuthorities()))
            .contains("ROLE_GUEST")
            .doesNotContain("ROLE_MANAGER", "ROLE_USER");
    }

    @Test
    void oidcSessionContractsAndSingleManagedTeamResolutionStayStable() throws Exception {
        Team team = saveTeam("Managers");
        saveTeamMember(team, "manager-user", TeamRole.MANAGER);

        MockHttpServletRequest request = authenticateOidcRequest("manager-user", "Manager User", "manager@example.com");
        HttpSession session = request.getSession(false);

        assertThat(session).isNotNull();
        assertThat(session.getAttribute("authType")).isEqualTo("OIDC");
        assertThat(session.getAttribute("authenticatedUser")).isEqualTo("manager-user");
        assertThat(session.getAttribute("userDisplayName")).isEqualTo("Manager User");
        assertThat(session.getAttribute("userEmail")).isEqualTo("manager@example.com");
        assertThat(authService.findSingleManagedTeamId(request))
            .contains(team.getId());
    }

    @Test
    void oidcUserManagingMultipleTeamsDoesNotResolveSingleManagedTeam() throws Exception {
        saveTeamMember(saveTeam("Managers One"), "manager-user", TeamRole.MANAGER);
        saveTeamMember(saveTeam("Managers Two"), "manager-user", TeamRole.MANAGER);

        MockHttpServletRequest request = authenticateOidcRequest("manager-user", "Manager User", "manager@example.com");

        assertThat(authService.findSingleManagedTeamId(request)).isEmpty();
    }

    private Authentication authenticateOidcUser(String username, String displayName, String email) throws Exception {
        authenticateOidcRequest(username, displayName, email);
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private MockHttpServletRequest authenticateOidcRequest(String username, String displayName, String email) throws Exception {
        SecurityContextHolder.clearContext();

        Map<String, Object> attributes = Map.of(
            "login", username,
            "name", displayName,
            "email", email,
            "sub", username
        );

        OAuth2User principal = new DefaultOAuth2User(List.of(), attributes, "login");
        OAuth2AuthenticationToken authentication = new OAuth2AuthenticationToken(principal, List.of(), "github");

        MockHttpServletRequest request = new MockHttpServletRequest();
        oidcSuccessHandler.onAuthenticationSuccess(request, new MockHttpServletResponse(), authentication);

        return request;
    }

    private Team saveTeam(String teamName) {
        Organization organization = new Organization();
        organization.setName(teamName + " Org");
        organization.setSlug(teamName.toLowerCase() + "-org-" + UUID.randomUUID());
        Organization savedOrganization = organizationRepository.saveAndFlush(organization);

        Team team = new Team();
        team.setName(teamName);
        team.setOrganization(savedOrganization);
        return teamRepository.saveAndFlush(team);
    }

    private void saveTeamMember(Team team, String username, TeamRole role) {
        TeamMember member = new TeamMember();
        member.setTeam(team);
        member.setUserId(authService.toOidcUserId(username));
        member.setRole(role);
        teamMemberRepository.saveAndFlush(member);
    }

    private List<String> authorityNames(Collection<? extends GrantedAuthority> authorities) {
        return authorities.stream().map(GrantedAuthority::getAuthority).toList();
    }

    @TestConfiguration
    static class ManagerAuthorizationEndpointConfig {

        @Bean
        ManagerOnlyProbeController managerOnlyProbeController() {
            return new ManagerOnlyProbeController();
        }
    }

    @RestController
    static class ManagerOnlyProbeController {

        @GetMapping("/api/manager/test-probe")
        @PreAuthorize("hasRole('MANAGER')")
        ResponseEntity<Map<String, String>> probe() {
            return ResponseEntity.ok(Map.of("status", "ok"));
        }
    }
}
