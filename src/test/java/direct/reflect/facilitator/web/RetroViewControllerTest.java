package direct.reflect.facilitator.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import direct.reflect.facilitator.facilitation.RetroSession;
import direct.reflect.facilitator.configurator.RetroTemplate;
import direct.reflect.facilitator.configurator.RetroStep;
import direct.reflect.facilitator.configurator.ComponentType;
import direct.reflect.facilitator.facilitation.Participant;
import direct.reflect.facilitator.facilitation.ParticipantRole;
import direct.reflect.facilitator.facilitation.RetroPhase;
import direct.reflect.facilitator.facilitation.response.ParticipantResponse;
import direct.reflect.facilitator.common.exception.ParticipantNotFoundException;
import direct.reflect.facilitator.facilitation.RetroSessionService;
import direct.reflect.facilitator.facilitation.ParticipantService;
import direct.reflect.facilitator.facilitation.response.ResponseService;
import direct.reflect.facilitator.auth.AuthService;
import direct.reflect.facilitator.web.RetroViewController;

@WebMvcTest(controllers = RetroViewController.class)
@EnableAutoConfiguration(exclude = {
    DataSourceAutoConfiguration.class,
    HibernateJpaAutoConfiguration.class,
    DataRedisAutoConfiguration.class
})
class RetroViewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RetroSessionService retroService;

    @MockitoBean
    private ParticipantService participantService;

    @MockitoBean
    private ResponseService responseService;

    @MockitoBean
    private AuthService authenticationHelper;


    @Test
    @WithMockUser
    void homePage_AuthenticatedUser_ShouldReturnOk() throws Exception {
        // Mock the templates call
        when(retroService.getAvailableTemplates())
            .thenReturn(java.util.Collections.emptyList());
        
        // Mock the authentication helper call
        when(authenticationHelper.getDisplayName(any()))
            .thenReturn("Test User");

        mockMvc.perform(get("/"))
            .andExpect(status().isOk())
            .andExpect(view().name("layout"))
            .andExpect(model().attribute("page", "home"))
            .andExpect(model().attribute("title", "Team Retrospective - Home"));
    }

    @Test
    void homePage_UnauthenticatedUser_ShouldRedirectToLogin() throws Exception {
        // Unauthenticated users should be redirected to login page
        mockMvc.perform(get("/"))
            .andExpect(status().isFound()); // OAuth2 redirects unauthenticated users to /login
    }

    @Test
    @WithMockUser
    void retroView_AuthenticatedUser_NotParticipating_ShouldRedirect() throws Exception {
        // Given
        UUID retroId = UUID.randomUUID();

        // Mock that participantService throws ParticipantNotFoundException
        when(participantService.getParticipantForSession(any(), eq(retroId)))
            .thenThrow(new ParticipantNotFoundException("Participant not found for session: " + retroId));

        // When & Then
        mockMvc.perform(get("/retro/{retroId}", retroId))
            .andExpect(status().is3xxRedirection());
    }

    @Test
    void retroView_UnauthenticatedUser_NotParticipating_ShouldRedirect() throws Exception {
        // Given
        UUID retroId = UUID.randomUUID();
        
        // When & Then - unauthenticated users always get redirected to /login
        mockMvc.perform(get("/retro/{retroId}", retroId))
            .andExpect(status().isFound()); // OAuth2 redirects to /login
    }

    @Test
    @WithMockUser(username = "authenticateduser")
    void retroView_AuthenticatedUser_WithCookie_ShouldWork() throws Exception {
        // Given
        UUID retroId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();
        RetroSession session = createMockSession(retroId, RetroPhase.LOBBY);
        Participant participant = createMockParticipant();
        participant.setUsername("authenticateduser"); // Should match @WithMockUser
        participant.setDisplayName("Authenticated User");

        // Mock the service calls
        when(retroService.getSessionById(retroId)).thenReturn(session);
        when(participantService.getParticipantForSession(any(), eq(retroId))).thenReturn(participant);
        when(participantService.isFacilitator(any(), eq(retroId))).thenReturn(false);
        when(participantService.getSessionParticipants(retroId)).thenReturn(java.util.Collections.singletonList(participant));

        // When & Then - Test with participant cookie
        mockMvc.perform(get("/retro/{retroId}", retroId)
                .cookie(new jakarta.servlet.http.Cookie("participantId", participantId.toString())))
            .andExpect(status().isOk())
            .andExpect(view().name("layout"))
            .andExpect(model().attributeExists("retroSession"))
            .andExpect(model().attributeExists("participant"));
    }

    @Test
    void retroView_UnauthenticatedUser_WithCookie_ShouldRedirect() throws Exception {
        // Given - unauthenticated users should be redirected regardless of cookies
        UUID retroId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();

        // When & Then - unauthenticated users always get redirected to /login
        mockMvc.perform(get("/retro/{retroId}", retroId)
                .cookie(new jakarta.servlet.http.Cookie("participantId", participantId.toString())))
            .andExpect(status().isFound()); // OAuth2 redirects to /login
    }

    @Test
    @WithMockUser
    void retroView_AuthenticatedUser_WithoutCookie_ShouldWork() throws Exception {
        // Given - when there's no cookie, service should generate one
        UUID retroId = UUID.randomUUID();
        RetroSession session = createMockSession(retroId, RetroPhase.LOBBY);
        Participant participant = createMockParticipant();
        participant.setUsername("authenticateduser");
        participant.setDisplayName("Authenticated User");

        // Mock the service calls
        when(retroService.getSessionById(retroId)).thenReturn(session);
        when(participantService.getParticipantForSession(any(), eq(retroId))).thenReturn(participant);
        when(participantService.isFacilitator(any(), eq(retroId))).thenReturn(false);
        when(participantService.getSessionParticipants(retroId)).thenReturn(java.util.Collections.singletonList(participant));

        // When & Then - Test without participant cookie (should still work)
        mockMvc.perform(get("/retro/{retroId}", retroId))
            .andExpect(status().isOk())
            .andExpect(view().name("layout"))
            .andExpect(model().attributeExists("retroSession"))
            .andExpect(model().attributeExists("participant"));
    }

    @Test
    void retroView_UnauthenticatedUser_WithoutCookie_ShouldRedirect() throws Exception {
        // Given - unauthenticated users should be redirected to login
        UUID retroId = UUID.randomUUID();

        // When & Then - unauthenticated users always get redirected to /login
        mockMvc.perform(get("/retro/{retroId}", retroId))
            .andExpect(status().isFound()); // OAuth2 redirects to /login
    }

    @ParameterizedTest
    @EnumSource(RetroPhase.class)
    @WithMockUser
    void retroView_AuthenticatedUser_AllPhases_ShouldWork(RetroPhase phase) throws Exception {
        // Given
        UUID retroId = UUID.randomUUID();
        RetroSession session = createMockSession(retroId, phase);
        Participant participant = createMockParticipant();
        participant.setUsername("authenticateduser");

        // Mock the service calls
        when(retroService.getSessionById(retroId)).thenReturn(session);
        when(participantService.getParticipantForSession(any(), eq(retroId))).thenReturn(participant);
        when(participantService.isFacilitator(any(), eq(retroId))).thenReturn(false);
        when(participantService.getSessionParticipants(retroId)).thenReturn(java.util.Collections.singletonList(participant));

        // For active phases, mock getCurrentStep
        if (phase != RetroPhase.LOBBY && phase != RetroPhase.CREATED) {
            when(retroService.getCurrentStep(retroId)).thenReturn(null);
        }

        // When & Then
        mockMvc.perform(get("/retro/{retroId}", retroId))
            .andExpect(status().isOk())
            .andExpect(view().name("layout"))
            .andExpect(model().attributeExists("retroSession"))
            .andExpect(model().attributeExists("participant"));
    }

    @ParameterizedTest
    @EnumSource(RetroPhase.class)
    void retroView_UnauthenticatedUser_AllPhases_ShouldRedirect(RetroPhase phase) throws Exception {
        // Given - unauthenticated users should be redirected regardless of phase
        UUID retroId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();

        // When & Then - unauthenticated users always get redirected to /login
        mockMvc.perform(get("/retro/{retroId}", retroId)
                .cookie(new jakarta.servlet.http.Cookie("participantId", participantId.toString())))
            .andExpect(status().isFound()); // OAuth2 redirects to /login
    }

    @Test
    @WithMockUser
    void retroView_AuthenticatedFacilitator_ShouldHaveFacilitatorAccess() throws Exception {
        // Given
        UUID retroId = UUID.randomUUID();
        RetroSession session = createMockSession(retroId, RetroPhase.LOBBY);
        Participant facilitator = createMockParticipant();
        facilitator.setUsername("facilitatoruser");
        facilitator.setDisplayName("Facilitator User");
        facilitator.setRole(ParticipantRole.FACILITATOR);

        // Mock the service calls - this time as facilitator
        when(retroService.getSessionById(retroId)).thenReturn(session);
        when(participantService.getParticipantForSession(any(), eq(retroId))).thenReturn(facilitator);
        when(participantService.isFacilitator(any(), eq(retroId))).thenReturn(true);
        when(participantService.getSessionParticipants(retroId)).thenReturn(java.util.Collections.singletonList(facilitator));

        // When & Then
        mockMvc.perform(get("/retro/{retroId}", retroId))
            .andExpect(status().isOk())
            .andExpect(view().name("layout"))
            .andExpect(model().attributeExists("retroSession"))
            .andExpect(model().attributeExists("participant"))
            .andExpect(model().attribute("isFacilitator", true));
    }

    @Test
    @WithMockUser
    void retroView_AuthenticatedUser_ParticipantNotFound_ShouldRedirect() throws Exception {
        // Given
        UUID retroId = UUID.randomUUID();

        // Mock that participantService throws ParticipantNotFoundException
        when(participantService.getParticipantForSession(any(), eq(retroId)))
            .thenThrow(new ParticipantNotFoundException("Participant not found for session: " + retroId));

        // When & Then
        mockMvc.perform(get("/retro/{retroId}", retroId))
            .andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser
    void getParticipantsList_AuthenticatedUser_ShouldReturnOk() throws Exception {
        // Given
        UUID retroId = UUID.randomUUID();
        when(participantService.getSessionParticipants(retroId))
            .thenReturn(java.util.Collections.emptyList());

        // When & Then
        mockMvc.perform(get("/retro/{retroId}/participants", retroId))
            .andExpect(status().isOk());
    }

    @Test
    void getParticipantsList_UnauthenticatedUser_ShouldRedirect() throws Exception {
        // Given - unauthenticated users should be redirected
        UUID retroId = UUID.randomUUID();

        // When & Then - unauthenticated users get redirected to /login
        mockMvc.perform(get("/retro/{retroId}/participants", retroId))
            .andExpect(status().isFound()); // OAuth2 redirects to /login
    }

    @Test
    void loginPage_UnauthenticatedUser_ShouldReturnOk() throws Exception {
        // Login page is public and should work for unauthenticated users
        mockMvc.perform(get("/login"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void loginPage_AuthenticatedUser_ShouldReturnOk() throws Exception {
        // Login page should also work for authenticated users
        mockMvc.perform(get("/login"))
            .andExpect(status().isOk());
    }


    @Test
    @WithMockUser
    void columnResponses_WithAllowVotingTrue_ShouldRenderVoteButton() throws Exception {
        // Given
        UUID retroId = UUID.randomUUID();
        Long stepId = 1L;
        RetroSession session = createMockSession(retroId, RetroPhase.GATHER_DATA);
        RetroStep step = createMockStep(stepId, true);
        ParticipantResponse response = createMockColumnResponse("Mad", "This is frustrating");
        UUID participantId = response.getParticipant().getParticipantId();

        when(participantService.canAccessRetro(retroId)).thenReturn(true);
        when(retroService.getSessionById(retroId)).thenReturn(session);
        when(retroService.getCurrentStep(retroId)).thenReturn(step);
        when(responseService.getResponsesForStageComponentType(any(), any(), eq(ComponentType.MULTI_COLUMN_BOARD)))
            .thenReturn(List.of(response));
        when(authenticationHelper.getParticipantId(any())).thenReturn(participantId);

        // When & Then
        mockMvc.perform(get("/retro/{retroId}/step/{stepId}/responses/column", retroId, stepId)
                .param("columnId", "Mad"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("👍")));
    }

    @Test
    @WithMockUser
    void columnResponses_WithAllowVotingFalse_ShouldNotRenderVoteButton() throws Exception {
        // Given
        UUID retroId = UUID.randomUUID();
        Long stepId = 1L;
        RetroSession session = createMockSession(retroId, RetroPhase.GATHER_DATA);
        RetroStep step = createMockStep(stepId, false);
        ParticipantResponse response = createMockColumnResponse("Mad", "This is frustrating");
        UUID participantId = response.getParticipant().getParticipantId();

        when(participantService.canAccessRetro(retroId)).thenReturn(true);
        when(retroService.getSessionById(retroId)).thenReturn(session);
        when(retroService.getCurrentStep(retroId)).thenReturn(step);
        when(responseService.getResponsesForStageComponentType(any(), any(), eq(ComponentType.MULTI_COLUMN_BOARD)))
            .thenReturn(List.of(response));
        when(authenticationHelper.getParticipantId(any())).thenReturn(participantId);

        // When & Then
        mockMvc.perform(get("/retro/{retroId}/step/{stepId}/responses/column", retroId, stepId)
                .param("columnId", "Mad"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("👍"))));
    }

    // Helper methods
    private RetroSession createMockSession(UUID retroId, RetroPhase phase) {
        RetroSession session = new RetroSession();
        session.setId(retroId);
        session.setName("Test Session");
        session.setPhase(phase);
        session.setCreatedAt(LocalDateTime.now());
        if (phase == RetroPhase.COMPLETED) {
            session.setFinishedAt(LocalDateTime.now());
        }

        // Create minimal template
        RetroTemplate template = new RetroTemplate();
        template.setId(1L);
        template.setName("Test Template");
        session.setTemplate(template);

        return session;
    }

    private Participant createMockParticipant() {
        Participant participant = new Participant();
        participant.setParticipantId(UUID.randomUUID());
        participant.setDisplayName("Test User");
        participant.setUsername("testuser");
        participant.setRole(ParticipantRole.PARTICIPANT);
        return participant;
    }


    private RetroStep createMockStep(Long stepId, boolean allowVoting) {
        RetroStep step = new RetroStep();
        step.setId(stepId);
        step.setComponentType(ComponentType.MULTI_COLUMN_BOARD);
        Map<String, Object> capabilities = new HashMap<>();
        capabilities.put("allowVoting", allowVoting);
        capabilities.put("allowInput", true);
        capabilities.put("showContent", true);
        capabilities.put("showVotes", true);
        capabilities.put("maxLength", 500);
        Map<String, Object> config = new HashMap<>();
        config.put("capabilities", capabilities);
        config.put("columns", List.of(
            Map.of("id", "mad", "title", "Mad", "color", "#EF4444"),
            Map.of("id", "sad", "title", "Sad", "color", "#3B82F6"),
            Map.of("id", "glad", "title", "Glad", "color", "#10B981")
        ));
        step.setComponentConfig(config);
        return step;
    }

    private ParticipantResponse createMockColumnResponse(String columnId, String content) {
        Participant participant = new Participant();
        participant.setParticipantId(UUID.randomUUID());
        participant.setDisplayName("Test User");
        participant.setRole(ParticipantRole.PARTICIPANT);

        ParticipantResponse response = new ParticipantResponse();
        response.setId(UUID.randomUUID());
        response.setParticipant(participant);
        response.setIsVisible(true);
        Map<String, Object> data = new HashMap<>();
        data.put("columnId", columnId);
        data.put("content", content);
        response.setResponseData(data);
        return response;
    }
}
