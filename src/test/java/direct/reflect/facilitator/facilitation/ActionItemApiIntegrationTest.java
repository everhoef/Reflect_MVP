package direct.reflect.facilitator.facilitation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import direct.reflect.facilitator.facilitation.actions.ActionItemDto;
import direct.reflect.facilitator.facilitation.actions.ActionItemRepository;
import direct.reflect.facilitator.facilitation.actions.ActionItemStatus;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.List;
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
class ActionItemApiIntegrationTest {

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
    private ParticipantRepository participantRepository;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private EventService eventService;

    private RetroSession testSession;
    private Participant sessionParticipant;
    private UsernamePasswordAuthenticationToken testAuth;

    @BeforeEach
    void setUp() {
        testAuth = new UsernamePasswordAuthenticationToken(
                "test-user",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));

        RetroSession session = new RetroSession();
        session.setName("Action Item API Session");
        testSession = sessionRepository.saveAndFlush(session);

        Participant participant = new Participant();
        participant.setParticipantId(UUID.randomUUID());
        participant.setSession(testSession);
        participant.setDisplayName("Facilitator User");
        participant.setRole(ParticipantRole.FACILITATOR);
        participant.setStatus(ParticipantStatus.ACTIVE);
        sessionParticipant = participantRepository.saveAndFlush(participant);

        when(authService.getParticipantId(any(HttpServletRequest.class))).thenReturn(sessionParticipant.getParticipantId());
    }

    @AfterEach
    void cleanUp() {
        actionItemRepository.deleteAll();
        participantRepository.deleteAll();
        sessionRepository.deleteAll();
    }

    @Test
    void createActionItem_validRequest_returnsCreatedAndPublishesActionCreatedEvent() throws Exception {
        mockMvc.perform(post("/api/retro/{retroId}/actions", testSession.getId())
                        .with(authentication(testAuth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "what": "Daily sync with design team",
                                  "who": "Alice",
                                  "dueDate": "2026-05-01",
                                  "successCriteria": "Attendance logged five days per week"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.what").value("Daily sync with design team"))
                .andExpect(jsonPath("$.who").value("Alice"))
                .andExpect(jsonPath("$.dueDate").value("2026-05-01"))
                .andExpect(jsonPath("$.successCriteria").value("Attendance logged five days per week"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.escalated").value(false));

        assertThat(actionItemRepository.findByRetroSessionId(testSession.getId()))
                .singleElement()
                .satisfies(actionItem -> {
                    assertThat(actionItem.getWhat()).isEqualTo("Daily sync with design team");
                    assertThat(actionItem.getWho()).isEqualTo("Alice");
                    assertThat(actionItem.getDueDate()).isEqualTo(LocalDate.of(2026, 5, 1));
                    assertThat(actionItem.getSuccessCriteria()).isEqualTo("Attendance logged five days per week");
                    assertThat(actionItem.getStatus()).isEqualTo(ActionItemStatus.OPEN);
                    assertThat(actionItem.getCreatedByParticipantId()).isEqualTo(sessionParticipant.getParticipantId());
                });

        ArgumentCaptor<RetroEvent> eventCaptor = ArgumentCaptor.forClass(RetroEvent.class);
        verify(eventService).publish(eventCaptor.capture());

        RetroEvent<?> publishedEvent = eventCaptor.getValue();
        assertThat(publishedEvent.type()).isEqualTo(RetroEvent.EventType.ACTION_CREATED);
        assertThat(publishedEvent.retroId()).isEqualTo(testSession.getId());
        assertThat(publishedEvent.sourceId()).isEqualTo(sessionParticipant.getParticipantId().toString());

        ActionItemDto payload = (ActionItemDto) publishedEvent.payload();
        assertThat(payload.what()).isEqualTo("Daily sync with design team");
        assertThat(payload.who()).isEqualTo("Alice");
        assertThat(payload.dueDate()).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(payload.status()).isEqualTo(ActionItemStatus.OPEN);
    }

    @Test
    void createActionItem_missingRequiredFields_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/retro/{retroId}/actions", testSession.getId())
                        .with(authentication(testAuth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "who": "Alice",
                                  "dueDate": "2026-05-01"
                                }
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/retro/{retroId}/actions", testSession.getId())
                        .with(authentication(testAuth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "what": "Daily sync with design team",
                                  "dueDate": "2026-05-01"
                                }
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/retro/{retroId}/actions", testSession.getId())
                        .with(authentication(testAuth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "what": "Daily sync with design team",
                                  "who": "Alice"
                                }
                                """))
                .andExpect(status().isBadRequest());

        assertThat(actionItemRepository.findByRetroSessionId(testSession.getId())).isEmpty();
        verifyNoInteractions(eventService);
    }

    @Test
    void getActionItems_returnsSessionScopedItems() throws Exception {
        saveActionItem("Daily sync with design team", "Alice", LocalDate.of(2026, 5, 1), "Attendance logged");
        saveActionItem("Review incidents weekly", "Bob", LocalDate.of(2026, 5, 8), null);

        mockMvc.perform(get("/api/retro/{retroId}/actions", testSession.getId())
                        .with(authentication(testAuth)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].what").value("Daily sync with design team"))
                .andExpect(jsonPath("$[0].who").value("Alice"))
                .andExpect(jsonPath("$[1].what").value("Review incidents weekly"))
                .andExpect(jsonPath("$[1].who").value("Bob"));

        verifyNoInteractions(eventService);
    }

    @Test
    void updateActionItem_validRequest_returnsUpdatedItemAndPublishesActionUpdatedEvent() throws Exception {
        ActionItem actionItem = saveActionItem("Daily sync with design team", "Alice", LocalDate.of(2026, 5, 1), null);

        mockMvc.perform(patch("/api/retro/{retroId}/actions/{actionId}", testSession.getId(), actionItem.getId())
                        .with(authentication(testAuth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "what": "Daily 15-minute sync with design team",
                                  "successCriteria": "Attendance logged every weekday"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(actionItem.getId().toString()))
                .andExpect(jsonPath("$.what").value("Daily 15-minute sync with design team"))
                .andExpect(jsonPath("$.who").value("Alice"))
                .andExpect(jsonPath("$.successCriteria").value("Attendance logged every weekday"));

        ActionItem updatedActionItem = actionItemRepository.findById(actionItem.getId()).orElseThrow();
        assertThat(updatedActionItem.getWhat()).isEqualTo("Daily 15-minute sync with design team");
        assertThat(updatedActionItem.getWho()).isEqualTo("Alice");
        assertThat(updatedActionItem.getSuccessCriteria()).isEqualTo("Attendance logged every weekday");

        ArgumentCaptor<RetroEvent> eventCaptor = ArgumentCaptor.forClass(RetroEvent.class);
        verify(eventService).publish(eventCaptor.capture());

        RetroEvent<?> publishedEvent = eventCaptor.getValue();
        assertThat(publishedEvent.type()).isEqualTo(RetroEvent.EventType.ACTION_UPDATED);
        ActionItemDto payload = (ActionItemDto) publishedEvent.payload();
        assertThat(payload.id()).isEqualTo(actionItem.getId());
        assertThat(payload.what()).isEqualTo("Daily 15-minute sync with design team");
        assertThat(payload.successCriteria()).isEqualTo("Attendance logged every weekday");
    }

    @Test
    void deleteActionItem_existingItem_returnsNoContentAndPublishesActionDeletedEvent() throws Exception {
        ActionItem actionItem = saveActionItem("Daily sync with design team", "Alice", LocalDate.of(2026, 5, 1), null);

        mockMvc.perform(delete("/api/retro/{retroId}/actions/{actionId}", testSession.getId(), actionItem.getId())
                        .with(authentication(testAuth))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(actionItemRepository.findById(actionItem.getId())).isEmpty();

        ArgumentCaptor<RetroEvent> eventCaptor = ArgumentCaptor.forClass(RetroEvent.class);
        verify(eventService).publish(eventCaptor.capture());

        RetroEvent<?> publishedEvent = eventCaptor.getValue();
        assertThat(publishedEvent.type()).isEqualTo(RetroEvent.EventType.ACTION_DELETED);
        ActionItemDto payload = (ActionItemDto) publishedEvent.payload();
        assertThat(payload.id()).isEqualTo(actionItem.getId());
        assertThat(payload.what()).isEqualTo("Daily sync with design team");
    }

    @Test
    void updateActionItemStatus_validRequest_returnsUpdatedStatus() throws Exception {
        ActionItem actionItem = saveActionItem("Daily sync with design team", "Alice", LocalDate.of(2026, 5, 1), null);

        mockMvc.perform(post("/api/retro/{retroId}/actions/{actionId}/status", testSession.getId(), actionItem.getId())
                        .with(authentication(testAuth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "DONE"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(actionItem.getId().toString()))
                .andExpect(jsonPath("$.status").value("DONE"));

        ActionItem updatedActionItem = actionItemRepository.findById(actionItem.getId()).orElseThrow();
        assertThat(updatedActionItem.getStatus()).isEqualTo(ActionItemStatus.DONE);

        ArgumentCaptor<RetroEvent> eventCaptor = ArgumentCaptor.forClass(RetroEvent.class);
        verify(eventService).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().type()).isEqualTo(RetroEvent.EventType.ACTION_UPDATED);
    }

    @Test
    void nonSessionParticipant_getsForbiddenJsonResponse() throws Exception {
        when(authService.getParticipantId(any(HttpServletRequest.class))).thenReturn(UUID.randomUUID());

        mockMvc.perform(get("/api/retro/{retroId}/actions", testSession.getId())
                        .with(authentication(testAuth)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"error\":\"Access denied\"}"));

        verifyNoInteractions(eventService);
    }

    private ActionItem saveActionItem(String what, String who, LocalDate dueDate, String successCriteria) {
        ActionItem actionItem = new ActionItem();
        actionItem.setRetroSession(testSession);
        actionItem.setCreatedByParticipantId(sessionParticipant.getParticipantId());
        actionItem.setWhat(what);
        actionItem.setWho(who);
        actionItem.setDueDate(dueDate);
        actionItem.setSuccessCriteria(successCriteria);
        return actionItemRepository.saveAndFlush(actionItem);
    }
}
