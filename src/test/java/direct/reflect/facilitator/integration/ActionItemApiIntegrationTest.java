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
import direct.reflect.facilitator.facilitation.actionitem.ActionItemRepository;
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

import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
        actionItemRepository.deleteAll();
        participantRepository.deleteAll();
        sessionRepository.delete(testSession);
    }

    @Test
    @WithMockUser(roles = "USER")
    void createActionItem_ValidRequest_ReturnsCreatedDto() throws Exception {
        String body = "{\"what\": \"Daily standup\", \"whenDate\": \"2026-03-01\", \"successCriteria\": \"Team attends\"}";

        String responseJson = mockMvc.perform(post("/api/retro/{retroId}/step/{stepId}/action-items",
                        testSession.getId(), testStep.getId())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.what").value("Daily standup"))
                .andExpect(jsonPath("$.successCriteria").value("Team attends"))
                .andReturn().getResponse().getContentAsString();

        log.debug("Created action item response: {}", responseJson);
    }

    @Test
    @WithMockUser(roles = "USER")
    void createActionItem_WithAssignedParticipant_ResolvesDisplayName() throws Exception {
        String participantId = testParticipant.getParticipantId().toString();
        String body = "{\"what\": \"Fix CI pipeline\", \"assignedToParticipantId\": \"" + participantId + "\"}";

        mockMvc.perform(post("/api/retro/{retroId}/step/{stepId}/action-items",
                        testSession.getId(), testStep.getId())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.what").value("Fix CI pipeline"))
                .andExpect(jsonPath("$.assignedToParticipantId").value(participantId))
                .andExpect(jsonPath("$.assignedToDisplayName").value("Test User"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void createActionItem_BlankWhat_Returns400() throws Exception {
        String body = "{\"what\": \"\", \"whenDate\": \"2026-03-01\"}";

        var result = mockMvc.perform(post("/api/retro/{retroId}/step/{stepId}/action-items",
                        testSession.getId(), testStep.getId())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    @WithMockUser(roles = "USER")
    void updateActionItem_ValidRequest_ReturnsUpdatedDto() throws Exception {
        String createBody = "{\"what\": \"Original task\"}";
        String createResponse = mockMvc.perform(post("/api/retro/{retroId}/step/{stepId}/action-items",
                        testSession.getId(), testStep.getId())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID itemId = UUID.fromString(createResponse.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1"));

        String updateBody = "{\"what\": \"Updated task\", \"successCriteria\": \"Done and reviewed\"}";
        mockMvc.perform(put("/api/retro/{retroId}/action-items/{id}",
                        testSession.getId(), itemId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.what").value("Updated task"))
                .andExpect(jsonPath("$.successCriteria").value("Done and reviewed"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void deleteActionItem_ValidRequest_Returns204() throws Exception {
        String createBody = "{\"what\": \"Task to delete\"}";
        String createResponse = mockMvc.perform(post("/api/retro/{retroId}/step/{stepId}/action-items",
                        testSession.getId(), testStep.getId())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID itemId = UUID.fromString(createResponse.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1"));

        mockMvc.perform(delete("/api/retro/{retroId}/action-items/{id}",
                        testSession.getId(), itemId)
                        .with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/retro/{retroId}/action-items", testSession.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getActionItemsBySession_ReturnsAllItems() throws Exception {
        String body1 = "{\"what\": \"First action item\"}";
        String body2 = "{\"what\": \"Second action item\"}";

        mockMvc.perform(post("/api/retro/{retroId}/step/{stepId}/action-items",
                        testSession.getId(), testStep.getId())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body1))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/retro/{retroId}/step/{stepId}/action-items",
                        testSession.getId(), testStep.getId())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body2))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/retro/{retroId}/action-items", testSession.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }
}
