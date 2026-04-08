package direct.reflect.facilitator.facilitation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.redis.testcontainers.RedisContainer;
import direct.reflect.facilitator.config.TestRedisConfig;
import direct.reflect.facilitator.config.TestSecurityOverride;
import direct.reflect.facilitator.facilitation.actions.ActionItem;
import direct.reflect.facilitator.facilitation.actions.ActionItemRepository;
import direct.reflect.facilitator.facilitation.actions.ActionItemStatus;
import direct.reflect.facilitator.organization.Organization;
import direct.reflect.facilitator.organization.OrganizationRepository;
import direct.reflect.facilitator.organization.Team;
import direct.reflect.facilitator.organization.TeamRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles({"import", "test"})
@Import({TestSecurityOverride.class, TestRedisConfig.class})
class ActionCarryOverApiIntegrationTest {

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
  private ActionItemRepository actionItemRepository;

  @Autowired
  private RetroSessionRepository sessionRepository;

  @Autowired
  private TeamRepository teamRepository;

  @Autowired
  private OrganizationRepository organizationRepository;

  @MockitoBean
  private ParticipantService participantService;

  private UsernamePasswordAuthenticationToken testAuth;

  @BeforeEach
  void setUp() {
    testAuth = new UsernamePasswordAuthenticationToken(
        "test-user",
        null,
        Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));

    when(participantService.canAccessRetro(any(UUID.class))).thenReturn(true);
  }

  @AfterEach
  void cleanUp() {
    actionItemRepository.deleteAll();
    sessionRepository.deleteAll();
    teamRepository.deleteAll();
    organizationRepository.deleteAll();
  }

  @Test
  void getPreviousActions_returnsOpenActionsFromMostRecentPriorCompletedSessionForSameTeam()
      throws Exception {
    Team alphaTeam = saveTeam("Alpha");

    RetroSession oldestCompleted = saveSession(
        "Alpha - Older Completed",
        alphaTeam,
        RetroPhase.COMPLETED,
        LocalDateTime.of(2026, 4, 1, 9, 0),
        LocalDateTime.of(2026, 4, 1, 10, 0));
    saveActionItem(
        oldestCompleted,
        "Retire manual checklist",
        "Alice",
        LocalDate.of(2026, 4, 10),
        "Checklist replaced",
        ActionItemStatus.OPEN);

    RetroSession latestCompleted = saveSession(
        "Alpha - Latest Completed",
        alphaTeam,
        RetroPhase.COMPLETED,
        LocalDateTime.of(2026, 4, 8, 9, 0),
        LocalDateTime.of(2026, 4, 8, 10, 0));
    saveActionItem(
        latestCompleted,
        "Start design sync",
        "Bob",
        LocalDate.of(2026, 4, 20),
        "Attendance logged",
        ActionItemStatus.OPEN);

    RetroSession currentSession = saveSession(
        "Alpha - Current",
        alphaTeam,
        RetroPhase.CREATED,
        LocalDateTime.of(2026, 4, 15, 9, 0),
        null);

    mockMvc.perform(get("/api/retro/{retroId}/previous-actions", currentSession.getId())
            .with(authentication(testAuth)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].what").value("Start design sync"))
        .andExpect(jsonPath("$[0].who").value("Bob"))
        .andExpect(jsonPath("$[0].dueDate").value("2026-04-20"))
        .andExpect(jsonPath("$[0].successCriteria").value("Attendance logged"))
        .andExpect(jsonPath("$[0].escalated").value(false))
        .andExpect(jsonPath("$[0].status").value(ActionItemStatus.OPEN.name()));
  }

  @Test
  void getPreviousActions_whenNoPreviousSessionExists_returnsEmptyArray() throws Exception {
    Team alphaTeam = saveTeam("Alpha");
    RetroSession currentSession = saveSession(
        "Alpha - First Retro",
        alphaTeam,
        RetroPhase.CREATED,
        LocalDateTime.of(2026, 4, 15, 9, 0),
        null);

    mockMvc.perform(get("/api/retro/{retroId}/previous-actions", currentSession.getId())
            .with(authentication(testAuth)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void getPreviousActions_whenPreviousSessionHasNoOpenActions_returnsEmptyArray() throws Exception {
    Team alphaTeam = saveTeam("Alpha");

    RetroSession previousSession = saveSession(
        "Alpha - Completed",
        alphaTeam,
        RetroPhase.COMPLETED,
        LocalDateTime.of(2026, 4, 1, 9, 0),
        LocalDateTime.of(2026, 4, 1, 10, 0));
    saveActionItem(
        previousSession,
        "Archive stale docs",
        "Alice",
        LocalDate.of(2026, 4, 10),
        "Docs archived",
        ActionItemStatus.DONE);

    RetroSession currentSession = saveSession(
        "Alpha - Current",
        alphaTeam,
        RetroPhase.CREATED,
        LocalDateTime.of(2026, 4, 15, 9, 0),
        null);

    mockMvc.perform(get("/api/retro/{retroId}/previous-actions", currentSession.getId())
            .with(authentication(testAuth)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void getPreviousActions_returnsOnlyOpenItems() throws Exception {
    Team alphaTeam = saveTeam("Alpha");

    RetroSession previousSession = saveSession(
        "Alpha - Completed",
        alphaTeam,
        RetroPhase.COMPLETED,
        LocalDateTime.of(2026, 4, 1, 9, 0),
        LocalDateTime.of(2026, 4, 1, 10, 0));
    saveActionItem(
        previousSession,
        "Introduce incident review",
        "Alice",
        LocalDate.of(2026, 4, 12),
        "Review held weekly",
        ActionItemStatus.OPEN);
    saveActionItem(
        previousSession,
        "Clean up old dashboard",
        "Bob",
        LocalDate.of(2026, 4, 14),
        "Dashboard removed",
        ActionItemStatus.DONE);

    RetroSession currentSession = saveSession(
        "Alpha - Current",
        alphaTeam,
        RetroPhase.CREATED,
        LocalDateTime.of(2026, 4, 15, 9, 0),
        null);

    mockMvc.perform(get("/api/retro/{retroId}/previous-actions", currentSession.getId())
            .with(authentication(testAuth)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].what").value("Introduce incident review"))
        .andExpect(jsonPath("$[0].status").value(ActionItemStatus.OPEN.name()));
  }

  @Test
  void getPreviousActions_doesNotLeakAcrossTeams() throws Exception {
    Team alphaTeam = saveTeam("Alpha");
    Team betaTeam = saveTeam("Beta");

    RetroSession alphaCompleted = saveSession(
        "Alpha - Completed",
        alphaTeam,
        RetroPhase.COMPLETED,
        LocalDateTime.of(2026, 4, 1, 9, 0),
        LocalDateTime.of(2026, 4, 1, 10, 0));
    saveActionItem(
        alphaCompleted,
        "Share API roadmap",
        "Alice",
        LocalDate.of(2026, 4, 11),
        "Roadmap shared",
        ActionItemStatus.OPEN);

    RetroSession betaCurrent = saveSession(
        "Beta - Current",
        betaTeam,
        RetroPhase.CREATED,
        LocalDateTime.of(2026, 4, 15, 9, 0),
        null);

    mockMvc.perform(get("/api/retro/{retroId}/previous-actions", betaCurrent.getId())
            .with(authentication(testAuth)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void getPreviousActions_whenCurrentSessionHasNoTeam_returnsEmptyArray() throws Exception {
    Team alphaTeam = saveTeam("Alpha");

    RetroSession alphaCompleted = saveSession(
        "Alpha - Completed",
        alphaTeam,
        RetroPhase.COMPLETED,
        LocalDateTime.of(2026, 4, 1, 9, 0),
        LocalDateTime.of(2026, 4, 1, 10, 0));
    saveActionItem(
        alphaCompleted,
        "Document rollout plan",
        "Alice",
        LocalDate.of(2026, 4, 9),
        "Plan published",
        ActionItemStatus.OPEN);

    RetroSession currentSession = saveSession(
        "Unscoped Retro",
        null,
        RetroPhase.CREATED,
        LocalDateTime.of(2026, 4, 15, 9, 0),
        null);

    mockMvc.perform(get("/api/retro/{retroId}/previous-actions", currentSession.getId())
            .with(authentication(testAuth)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  private RetroSession saveSession(
      String name,
      Team team,
      RetroPhase phase,
      LocalDateTime createdAt,
      LocalDateTime finishedAt) {
    RetroSession session = new RetroSession();
    session.setName(name);
    session.setTeam(team);
    session.setPhase(phase);
    session.setCreatedAt(createdAt);
    session.setFinishedAt(finishedAt);
    return sessionRepository.saveAndFlush(session);
  }

  private ActionItem saveActionItem(
      RetroSession retroSession,
      String what,
      String who,
      LocalDate dueDate,
      String successCriteria,
      ActionItemStatus status) {
    ActionItem actionItem = new ActionItem();
    actionItem.setRetroSession(retroSession);
    actionItem.setWhat(what);
    actionItem.setWho(who);
    actionItem.setDueDate(dueDate);
    actionItem.setSuccessCriteria(successCriteria);
    actionItem.setStatus(status);
    return actionItemRepository.saveAndFlush(actionItem);
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
}
