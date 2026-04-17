package direct.reflect.facilitator.facilitation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.redis.testcontainers.RedisContainer;
import direct.reflect.facilitator.auth.AuthService;
import direct.reflect.facilitator.config.TestRedisConfig;
import direct.reflect.facilitator.config.TestSecurityOverride;
import direct.reflect.facilitator.eventing.EventService;
import direct.reflect.facilitator.eventing.RetroEvent;
import direct.reflect.facilitator.facilitation.actions.ActionItem;
import direct.reflect.facilitator.facilitation.actions.ActionItemRepository;
import direct.reflect.facilitator.facilitation.escalation.EscalatedItem;
import direct.reflect.facilitator.facilitation.escalation.EscalatedItemRepository;
import direct.reflect.facilitator.facilitation.escalation.EscalatedItemVote;
import direct.reflect.facilitator.facilitation.escalation.EscalatedItemVoteId;
import direct.reflect.facilitator.facilitation.escalation.EscalatedItemVoteRepository;
import direct.reflect.facilitator.organization.Organization;
import direct.reflect.facilitator.organization.OrganizationRepository;
import direct.reflect.facilitator.organization.Team;
import direct.reflect.facilitator.organization.TeamMember;
import direct.reflect.facilitator.organization.TeamMemberRepository;
import direct.reflect.facilitator.organization.TeamRepository;
import direct.reflect.facilitator.organization.TeamRole;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
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
class EscalationApiIntegrationTest {

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
    private EscalatedItemRepository escalatedItemRepository;

    @Autowired
    private EscalatedItemVoteRepository escalatedItemVoteRepository;

    @Autowired
    private ParticipantRepository participantRepository;

    @Autowired
    private RetroSessionRepository sessionRepository;

    @Autowired
    private TeamMemberRepository teamMemberRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private TeamBackedRetroFixture teamBackedRetroFixture;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private EventService eventService;

    private UsernamePasswordAuthenticationToken participantAuth;
    private UsernamePasswordAuthenticationToken managerAuth;
    private UsernamePasswordAuthenticationToken nonManagerAuth;
    private UUID managerUserId;

    @BeforeEach
    void setUp() {
        participantAuth = new UsernamePasswordAuthenticationToken(
                "retro-user",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        managerAuth = new UsernamePasswordAuthenticationToken(
                "manager-user",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("ROLE_MANAGER")));
        nonManagerAuth = new UsernamePasswordAuthenticationToken(
                "member-user",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));

        managerUserId = UUID.randomUUID();
        when(authService.toOidcUserId("manager-user")).thenReturn(managerUserId);
        when(authService.findSingleManagedTeam(any(HttpServletRequest.class))).thenReturn(Optional.empty());
    }

    @AfterEach
    void cleanUp() {
        escalatedItemVoteRepository.deleteAll();
        escalatedItemRepository.deleteAll();
        actionItemRepository.deleteAll();
        participantRepository.deleteAll();
        sessionRepository.deleteAll();
        teamMemberRepository.deleteAll();
        teamRepository.deleteAll();
        organizationRepository.deleteAll();
    }

    @Test
    void escalateAction_createsEscalatedItemAndCalculatesThreshold() throws Exception {
        RetroSession retroSession = teamBackedRetroFixture.createTeamBackedSession("Platform");
        Team team = retroSession.getTeam();
        Participant facilitator = saveParticipant(retroSession, "Facilitator", ParticipantRole.FACILITATOR);
        saveParticipant(retroSession, "Alice", ParticipantRole.PARTICIPANT);
        saveParticipant(retroSession, "Bob", ParticipantRole.PARTICIPANT);
        saveParticipant(retroSession, "Carol", ParticipantRole.PARTICIPANT);
        saveParticipant(retroSession, "Dave", ParticipantRole.PARTICIPANT);
        setCurrentParticipant(facilitator);

        ActionItem actionItem = saveActionItem(retroSession, "Stabilize cross-team deployment handoff");

        mockMvc.perform(post("/api/retro/{retroId}/actions/{actionId}/escalate", retroSession.getId(), actionItem.getId())
                        .with(authentication(participantAuth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" +
                                "\"problemDescription\":\"Cross-team API dependency blocking releases\"" +
                                "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.problemDescription").value("Cross-team API dependency blocking releases"))
                .andExpect(jsonPath("$.voteCount").value(0))
                .andExpect(jsonPath("$.threshold").value(3))
                .andExpect(jsonPath("$.thresholdMet").value(false));

        EscalatedItem escalatedItem = escalatedItemRepository.findAll().getFirst();
        assertThat(escalatedItem.getRetroSession().getId()).isEqualTo(retroSession.getId());
        assertThat(escalatedItem.getTeam().getId()).isEqualTo(team.getId());
        assertThat(escalatedItem.getVoteThreshold()).isEqualTo(3);
        assertThat(escalatedItem.getProblemDescription()).isEqualTo("Cross-team API dependency blocking releases");

        ActionItem reloadedActionItem = actionItemRepository.findById(actionItem.getId()).orElseThrow();
        assertThat(reloadedActionItem.getEscalated()).isTrue();
    }

    @Test
    void escalateAction_rejectsEmptyProblemDescription() throws Exception {
        RetroSession retroSession = teamBackedRetroFixture.createTeamBackedSession("Platform");
        Participant facilitator = saveParticipant(retroSession, "Facilitator", ParticipantRole.FACILITATOR);
        saveParticipant(retroSession, "Alice", ParticipantRole.PARTICIPANT);
        setCurrentParticipant(facilitator);

        ActionItem actionItem = saveActionItem(retroSession, "Clarify cross-team release ownership");

        mockMvc.perform(post("/api/retro/{retroId}/actions/{actionId}/escalate", retroSession.getId(), actionItem.getId())
                        .with(authentication(participantAuth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" +
                                "\"problemDescription\":\"\"" +
                                "}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Validation failed: Escalated problem description is required"));

        assertThat(escalatedItemRepository.findAll()).isEmpty();
        assertThat(actionItemRepository.findById(actionItem.getId()).orElseThrow().getEscalated()).isFalse();
    }

    @Test
    void escalateAction_rejectsAlreadyEscalatedAction() throws Exception {
        RetroSession retroSession = teamBackedRetroFixture.createTeamBackedSession("Platform");
        Participant facilitator = saveParticipant(retroSession, "Facilitator", ParticipantRole.FACILITATOR);
        saveParticipant(retroSession, "Alice", ParticipantRole.PARTICIPANT);
        setCurrentParticipant(facilitator);

        ActionItem actionItem = saveActionItem(retroSession, "Stabilize release ownership");

        mockMvc.perform(post("/api/retro/{retroId}/actions/{actionId}/escalate", retroSession.getId(), actionItem.getId())
                        .with(authentication(participantAuth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" +
                                "\"problemDescription\":\"Cross-team API dependency blocking releases\"" +
                                "}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/retro/{retroId}/actions/{actionId}/escalate", retroSession.getId(), actionItem.getId())
                        .with(authentication(participantAuth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" +
                                "\"problemDescription\":\"Another escalation attempt should be rejected\"" +
                                "}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Invalid request"));

        assertThat(escalatedItemRepository.findAll())
                .singleElement()
                .satisfies(escalatedItem -> assertThat(escalatedItem.getProblemDescription())
                        .isEqualTo("Cross-team API dependency blocking releases"));
        assertThat(actionItemRepository.findById(actionItem.getId()).orElseThrow().getEscalated()).isTrue();
    }

    @Test
    void escalateAction_succeedsForSessionAutoLinkedToCreatorsOnlyManagedTeam() throws Exception {
        Organization organization = new Organization();
        organization.setName("Platform Org");
        organization.setSlug("platform-org-" + UUID.randomUUID());
        organization = organizationRepository.saveAndFlush(organization);

        Team team = new Team();
        team.setName("Platform");
        team.setOrganization(organization);
        team = teamRepository.saveAndFlush(team);

        UUID participantId = UUID.randomUUID();
        when(authService.getParticipantId(any(HttpServletRequest.class))).thenReturn(participantId);
        when(authService.getDisplayName(any(HttpServletRequest.class))).thenReturn("Manager Facilitator");
        when(authService.getUsername(any(HttpServletRequest.class))).thenReturn("manager-user");
        when(authService.findSingleManagedTeam(any(HttpServletRequest.class))).thenReturn(Optional.of(team));

        mockMvc.perform(post("/api/retro/create")
                        .with(authentication(managerAuth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" +
                                "\"sessionName\":\"Platform Retro\"" +
                                "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionName").value("Platform Retro"));

        Participant facilitator = participantRepository.findByParticipantId(participantId).getFirst();
        RetroSession retroSession = sessionRepository.findById(facilitator.getSession().getId()).orElseThrow();

        assertThat(retroSession.getTeam()).isNotNull();
        assertThat(retroSession.getTeam().getId()).isEqualTo(team.getId());

        ActionItem actionItem = saveActionItem(retroSession, "Stabilize release ownership");
        setCurrentParticipant(facilitator);

        mockMvc.perform(post("/api/retro/{retroId}/actions/{actionId}/escalate", retroSession.getId(), actionItem.getId())
                        .with(authentication(managerAuth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" +
                                "\"problemDescription\":\"Cross-team release ownership is unresolved\"" +
                                "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.problemDescription").value("Cross-team release ownership is unresolved"))
                .andExpect(jsonPath("$.threshold").value(1));

        UUID teamId = team.getId();
        assertThat(escalatedItemRepository.findAll())
                .singleElement()
                .satisfies(escalatedItem -> assertThat(escalatedItem.getTeam().getId()).isEqualTo(teamId));
    }

    @Test
    void escalateAction_rejectsTeamlessSession() throws Exception {
        RetroSession retroSession = new RetroSession();
        retroSession.setName("Company Retro");
        retroSession.setPhase(RetroPhase.CREATED);
        retroSession.setCreatedAt(LocalDateTime.now());
        retroSession = sessionRepository.saveAndFlush(retroSession);

        Participant facilitator = saveParticipant(retroSession, "Facilitator", ParticipantRole.FACILITATOR);
        saveParticipant(retroSession, "Alice", ParticipantRole.PARTICIPANT);
        setCurrentParticipant(facilitator);

        ActionItem actionItem = saveActionItem(retroSession, "Clarify org-wide deployment process");

        mockMvc.perform(post("/api/retro/{retroId}/actions/{actionId}/escalate", retroSession.getId(), actionItem.getId())
                        .with(authentication(participantAuth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" +
                                "\"problemDescription\":\"No owning team exists for this retrospective\"" +
                                "}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Invalid request"));

        assertThat(escalatedItemRepository.findAll()).isEmpty();
        assertThat(actionItemRepository.findById(actionItem.getId()).orElseThrow().getEscalated()).isFalse();
    }

    @Test
    void voteEscalation_togglesVoteAndPublishesEscalationVoteUpdated() throws Exception {
        RetroSession retroSession = teamBackedRetroFixture.createTeamBackedSession("Delivery");
        Participant facilitator = saveParticipant(retroSession, "Facilitator", ParticipantRole.FACILITATOR);
        Participant voter = saveParticipant(retroSession, "Alice", ParticipantRole.PARTICIPANT);
        saveParticipant(retroSession, "Bob", ParticipantRole.PARTICIPANT);
        setCurrentParticipant(voter);
        long initialSyncVersion = sessionRepository.findById(retroSession.getId()).orElseThrow().getSyncVersion();

        EscalatedItem escalatedItem = saveEscalation(
                retroSession,
                "Release coordination needs manager intervention",
                2,
                LocalDateTime.of(2026, 4, 7, 10, 0));

        mockMvc.perform(post("/api/retro/{retroId}/escalations/{escalationId}/vote", retroSession.getId(), escalatedItem.getId())
                        .with(authentication(participantAuth))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.syncVersion").isNumber())
                .andExpect(jsonPath("$.escalationId").value(escalatedItem.getId().toString()))
                .andExpect(jsonPath("$.voteCount").value(1))
                .andExpect(jsonPath("$.threshold").value(2))
                .andExpect(jsonPath("$.thresholdMet").value(false))
                .andExpect(jsonPath("$.voted").value(true));

        long afterFirstVoteSyncVersion = sessionRepository.findById(retroSession.getId()).orElseThrow().getSyncVersion();
        assertThat(afterFirstVoteSyncVersion).isGreaterThan(initialSyncVersion);

        assertThat(escalatedItemVoteRepository.countByEscalatedItemId(escalatedItem.getId())).isEqualTo(1);

        mockMvc.perform(post("/api/retro/{retroId}/escalations/{escalationId}/vote", retroSession.getId(), escalatedItem.getId())
                        .with(authentication(participantAuth))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.syncVersion").value(afterFirstVoteSyncVersion + 1))
                .andExpect(jsonPath("$.voteCount").value(0))
                .andExpect(jsonPath("$.threshold").value(2))
                .andExpect(jsonPath("$.thresholdMet").value(false))
                .andExpect(jsonPath("$.voted").value(false));

        assertThat(escalatedItemVoteRepository.countByEscalatedItemId(escalatedItem.getId())).isZero();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<RetroEvent<?>> eventCaptor = ArgumentCaptor.forClass((Class) RetroEvent.class);
        verify(eventService, times(2)).publish(eventCaptor.capture());

        List<RetroEvent<?>> publishedEvents = eventCaptor.getAllValues();
        assertThat(publishedEvents)
                .extracting(RetroEvent::type)
                .containsOnly(RetroEvent.EventType.ESCALATION_VOTE_UPDATED);

        RetroEvent.EscalationVoteData firstPayload = (RetroEvent.EscalationVoteData) publishedEvents.get(0).payload();
        assertThat(firstPayload.escalationId()).isEqualTo(escalatedItem.getId().toString());
        assertThat(firstPayload.voteCount()).isEqualTo(1);
        assertThat(firstPayload.threshold()).isEqualTo(2);
        assertThat(firstPayload.thresholdMet()).isFalse();

        RetroEvent.EscalationVoteData secondPayload = (RetroEvent.EscalationVoteData) publishedEvents.get(1).payload();
        assertThat(secondPayload.voteCount()).isZero();
        assertThat(secondPayload.thresholdMet()).isFalse();

        assertThat(participantRepository.findBySession_Id(retroSession.getId()))
                .extracting(Participant::getParticipantId)
                .contains(facilitator.getParticipantId(), voter.getParticipantId());
    }

    @Test
    void getEscalations_returnsVoteCountsAndFacilitatorTieBreakThresholdMet() throws Exception {
        RetroSession retroSession = teamBackedRetroFixture.createTeamBackedSession("Enablement");
        retroSession.setSyncVersion(8L);
        sessionRepository.saveAndFlush(retroSession);
        Participant facilitator = saveParticipant(retroSession, "Facilitator", ParticipantRole.FACILITATOR);
        Participant participantOne = saveParticipant(retroSession, "Alice", ParticipantRole.PARTICIPANT);
        Participant participantTwo = saveParticipant(retroSession, "Bob", ParticipantRole.PARTICIPANT);
        saveParticipant(retroSession, "Carol", ParticipantRole.PARTICIPANT);
        setCurrentParticipant(participantOne);

        EscalatedItem facilitatorTieBreak = saveEscalation(
                retroSession,
                "Manager prioritization needed for vendor access",
                3,
                LocalDateTime.of(2026, 4, 7, 10, 0));
        saveVote(facilitatorTieBreak, facilitator);
        saveVote(facilitatorTieBreak, participantOne);

        EscalatedItem stillBelowThreshold = saveEscalation(
                retroSession,
                "Budget alignment still blocked",
                3,
                LocalDateTime.of(2026, 4, 7, 11, 0));
        saveVote(stillBelowThreshold, participantOne);
        saveVote(stillBelowThreshold, participantTwo);

        mockMvc.perform(get("/api/retro/{retroId}/escalations", retroSession.getId())
                        .with(authentication(participantAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.syncVersion").value(8))
                .andExpect(jsonPath("$.data[0].syncVersion").value(8))
                .andExpect(jsonPath("$.data[0].problemDescription").value("Manager prioritization needed for vendor access"))
                .andExpect(jsonPath("$.data[0].voteCount").value(2))
                .andExpect(jsonPath("$.data[0].threshold").value(3))
                .andExpect(jsonPath("$.data[0].thresholdMet").value(true))
                .andExpect(jsonPath("$.data[1].syncVersion").value(8))
                .andExpect(jsonPath("$.data[1].problemDescription").value("Budget alignment still blocked"))
                .andExpect(jsonPath("$.data[1].voteCount").value(2))
                .andExpect(jsonPath("$.data[1].threshold").value(3))
                .andExpect(jsonPath("$.data[1].thresholdMet").value(false));
    }

    @Test
    void getEscalations_returnsAuthoritativeSyncVersionWhenListIsEmpty() throws Exception {
        RetroSession retroSession = teamBackedRetroFixture.createTeamBackedSession("Empty Escalations");
        Participant participant = saveParticipant(retroSession, "Alice", ParticipantRole.PARTICIPANT);
        setCurrentParticipant(participant);
        retroSession.setSyncVersion(9L);
        sessionRepository.saveAndFlush(retroSession);

        mockMvc.perform(get("/api/retro/{retroId}/escalations", retroSession.getId())
                        .with(authentication(participantAuth)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.syncVersion").value(9))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void managerEscalations_nonManagerIsForbiddenForListAndDetail() throws Exception {
        UUID escalationId = UUID.randomUUID();

        mockMvc.perform(get("/api/manager/escalations").with(authentication(nonManagerAuth)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"error\":\"Access denied\"}"));

        mockMvc.perform(get("/api/manager/escalations/{id}", escalationId).with(authentication(nonManagerAuth)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"error\":\"Access denied\"}"));
    }

    @Test
    void managerEscalations_managerSeesOnlyThresholdMetItemsForManagedTeams() throws Exception {
        RetroSession managedRetro = teamBackedRetroFixture.createTeamBackedSession("Managed");
        Team managedTeam = managedRetro.getTeam();
        RetroSession otherRetro = teamBackedRetroFixture.createTeamBackedSession("Other");

        saveManagerMembership(managedTeam, managerUserId);

        Participant facilitator = saveParticipant(managedRetro, "Facilitator", ParticipantRole.FACILITATOR);
        Participant participantOne = saveParticipant(managedRetro, "Alice", ParticipantRole.PARTICIPANT);
        Participant participantTwo = saveParticipant(managedRetro, "Bob", ParticipantRole.PARTICIPANT);
        saveParticipant(managedRetro, "Carol", ParticipantRole.PARTICIPANT);

        EscalatedItem thresholdMet = saveEscalation(
                managedRetro,
                "Cross-team dependency blocks releases",
                3,
                LocalDateTime.of(2026, 4, 7, 10, 0));
        saveVote(thresholdMet, facilitator);
        saveVote(thresholdMet, participantOne);

        EscalatedItem belowThreshold = saveEscalation(
                managedRetro,
                "Tooling upgrade needs budget",
                3,
                LocalDateTime.of(2026, 4, 7, 11, 0));
        saveVote(belowThreshold, participantOne);

        Participant otherFacilitator = saveParticipant(otherRetro, "Other Facilitator", ParticipantRole.FACILITATOR);
        Participant otherParticipant = saveParticipant(otherRetro, "Other Alice", ParticipantRole.PARTICIPANT);
        EscalatedItem foreignThresholdMet = saveEscalation(
                otherRetro,
                "Other team issue should stay hidden",
                2,
                LocalDateTime.of(2026, 4, 7, 12, 0));
        saveVote(foreignThresholdMet, otherFacilitator);
        saveVote(foreignThresholdMet, otherParticipant);

        mockMvc.perform(get("/api/manager/escalations").with(authentication(managerAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(thresholdMet.getId().toString()))
                .andExpect(jsonPath("$[0].problemDescription").value("Cross-team dependency blocks releases"))
                .andExpect(jsonPath("$[0].voteCount").value(2))
                .andExpect(jsonPath("$[0].threshold").value(3))
                .andExpect(jsonPath("$[0].thresholdMet").value(true));

        mockMvc.perform(get("/api/manager/escalations/{id}", thresholdMet.getId()).with(authentication(managerAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(thresholdMet.getId().toString()))
                .andExpect(jsonPath("$.problemDescription").value("Cross-team dependency blocks releases"))
                .andExpect(jsonPath("$.voteCount").value(2))
                .andExpect(jsonPath("$.thresholdMet").value(true));
    }

    private void setCurrentParticipant(Participant participant) {
        when(authService.getParticipantId(any(HttpServletRequest.class))).thenReturn(participant.getParticipantId());
    }

    private ActionItem saveActionItem(RetroSession retroSession, String what) {
        ActionItem actionItem = new ActionItem();
        actionItem.setRetroSession(retroSession);
        actionItem.setWhat(what);
        actionItem.setWho("Alice");
        actionItem.setDueDate(LocalDate.of(2026, 4, 30));
        actionItem.setSuccessCriteria("Clear ownership across teams");
        return actionItemRepository.saveAndFlush(actionItem);
    }

    private EscalatedItem saveEscalation(
            RetroSession retroSession,
            String problemDescription,
            int threshold,
            LocalDateTime createdAt) {
        EscalatedItem escalatedItem = new EscalatedItem();
        escalatedItem.setRetroSession(retroSession);
        escalatedItem.setTeam(retroSession.getTeam());
        escalatedItem.setProblemDescription(problemDescription);
        escalatedItem.setVoteThreshold(threshold);
        escalatedItem.setCreatedAt(createdAt);
        return escalatedItemRepository.saveAndFlush(escalatedItem);
    }

    private void saveVote(EscalatedItem escalatedItem, Participant participant) {
        EscalatedItemVote vote = new EscalatedItemVote();
        vote.setId(new EscalatedItemVoteId(escalatedItem.getId(), participant.getParticipantId()));
        vote.setEscalatedItem(escalatedItem);
        escalatedItemVoteRepository.saveAndFlush(vote);
    }

    private Participant saveParticipant(RetroSession retroSession, String displayName, ParticipantRole role) {
        Participant participant = new Participant();
        participant.setParticipantId(UUID.randomUUID());
        participant.setSession(retroSession);
        participant.setDisplayName(displayName);
        participant.setRole(role);
        participant.setStatus(ParticipantStatus.ACTIVE);
        return participantRepository.saveAndFlush(participant);
    }

    private void saveManagerMembership(Team team, UUID userId) {
        TeamMember teamMember = new TeamMember();
        teamMember.setTeam(team);
        teamMember.setUserId(userId);
        teamMember.setRole(TeamRole.MANAGER);
        teamMemberRepository.saveAndFlush(teamMember);
    }
}
