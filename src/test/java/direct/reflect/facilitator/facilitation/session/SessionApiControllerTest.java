package direct.reflect.facilitator.facilitation.session;

import direct.reflect.facilitator.facilitation.participant.Participant;
import direct.reflect.facilitator.facilitation.participant.ParticipantRole;
import direct.reflect.facilitator.facilitation.participant.ParticipantService;
import direct.reflect.facilitator.facilitation.session.dto.AssistantStateDto;
import direct.reflect.facilitator.configurator.RetroStepQueryService;
import direct.reflect.facilitator.configurator.RetroTemplate;
import direct.reflect.facilitator.configurator.RetroStage;
import direct.reflect.facilitator.facilitation.session.dto.TimerStateDto;
import direct.reflect.facilitator.auth.AuthService;
import direct.reflect.facilitator.facilitation.response.ResponseService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {
    SessionApiController.class,
    direct.reflect.facilitator.auth.AuthController.class
})
@Import({
    direct.reflect.facilitator.config.TestSecurityOverride.class,
    AuthService.class
})
@EnableAutoConfiguration(exclude = {
    DataSourceAutoConfiguration.class,
    HibernateJpaAutoConfiguration.class
})
@org.springframework.test.context.ActiveProfiles("test")
public class SessionApiControllerTest {

    @Autowired
    private MockMvc mockMvc;
    
    @MockitoBean 
    private RetroSessionService retroSessionService;
    
    @MockitoBean 
    private ParticipantService participantService;

    @MockitoBean
    private ResponseService responseService;

    @MockitoBean
    private AuthService authHelper;

    @MockitoBean
    private RetroStepQueryService retroStepQueryService;

    @MockitoBean
    private RetroSyncVersionService retroSyncVersionService;

    @MockitoBean
    private direct.reflect.facilitator.eventing.EventService eventService;

    @Test
    @WithMockUser(roles = "USER")
    void shouldCreateRetrospectiveAndReturnJson() throws Exception {
        UUID retroId = UUID.randomUUID();
        RetroSession mockSession = new RetroSession();
        mockSession.setId(retroId); 
        mockSession.setName("Test Session");
        
        when(retroSessionService.createSessionWithFacilitator(eq("Test Session"), any(HttpServletRequest.class)))
            .thenReturn(mockSession);

        mockMvc.perform(post("/api/retros")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionName\":\"Test Session\"}"))
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.retroId").value(retroId.toString()))
                .andExpect(jsonPath("$.redirectUrl").value("/retro/" + retroId))
                .andExpect(jsonPath("$.sessionName").value("Test Session"));
    }

    @Test
    @WithMockUser(roles = "GUEST")
    void shouldAllowGuestUserCreatingSession() throws Exception {
        UUID retroId = UUID.randomUUID();
        RetroSession mockSession = new RetroSession();
        mockSession.setId(retroId);
        mockSession.setName("Test Session");

        when(retroSessionService.createSessionWithFacilitator(eq("Test Session"), any(HttpServletRequest.class)))
            .thenReturn(mockSession);

        mockMvc.perform(post("/api/retros")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionName\":\"Test Session\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.retroId").value(retroId.toString()))
                .andExpect(jsonPath("$.redirectUrl").value("/retro/" + retroId));
    }

    @Test
    @WithMockUser(roles = "USER")
    void nextStep_Facilitator_ShouldReturnJsonWithAdvancedTrue() throws Exception {
        UUID retroId = UUID.randomUUID();

        when(participantService.isFacilitator(any(HttpServletRequest.class), eq(retroId)))
            .thenReturn(true);

        mockMvc.perform(post("/api/retros/{retroId}/advance", retroId)
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.retroId").value(retroId.toString()))
                .andExpect(jsonPath("$.advanced").value(true));
    }

    @Test
    @WithMockUser(roles = "USER")
    void nextStep_NonFacilitator_ShouldReturnForbidden() throws Exception {
        UUID retroId = UUID.randomUUID();

        when(participantService.isFacilitator(any(HttpServletRequest.class), eq(retroId)))
            .thenReturn(false);

        mockMvc.perform(post("/api/retros/{retroId}/advance", retroId)
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    void getTimerState_ValidParticipant_ShouldReturnTimerState() throws Exception {
        UUID retroId = UUID.randomUUID();
        TimerStateDto timerState = new TimerStateDto(300, false, "green");

        when(participantService.isParticipating(any(HttpServletRequest.class), eq(retroId))).thenReturn(true);
        when(retroSessionService.getTimerState(retroId)).thenReturn(timerState);
        when(retroSyncVersionService.getSyncVersion(retroId)).thenReturn(12L);

        mockMvc.perform(get("/api/retros/{retroId}/timer", retroId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.syncVersion").value(12))
                .andExpect(jsonPath("$.data.remainingSeconds").value(300))
                .andExpect(jsonPath("$.data.isPaused").value(false))
                .andExpect(jsonPath("$.data.state").value("green"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getTimerState_NoTimerForStep_ShouldReturnNoContent() throws Exception {
        UUID retroId = UUID.randomUUID();

        when(participantService.isParticipating(any(HttpServletRequest.class), eq(retroId))).thenReturn(true);
        when(retroSessionService.getTimerState(retroId)).thenReturn(null);

        mockMvc.perform(get("/api/retros/{retroId}/timer", retroId))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "USER")
    void getTimerState_NotParticipant_ShouldReturnForbidden() throws Exception {
        UUID retroId = UUID.randomUUID();

        when(participantService.isParticipating(any(HttpServletRequest.class), eq(retroId))).thenReturn(false);

        mockMvc.perform(get("/api/retros/{retroId}/timer", retroId))
                .andExpect(status().isForbidden());
    }

    @Test
    void getTimerState_Unauthenticated_ShouldReturnUnauthorized() throws Exception {
        UUID retroId = UUID.randomUUID();

        mockMvc.perform(get("/api/retros/{retroId}/timer", retroId)
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    void pauseTimer_Facilitator_ShouldReturnOk() throws Exception {
        UUID retroId = UUID.randomUUID();

        when(participantService.isFacilitator(any(HttpServletRequest.class), eq(retroId)))
            .thenReturn(true);

        mockMvc.perform(post("/api/retros/{retroId}/timer/pause", retroId)
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    void pauseTimer_NonFacilitator_ShouldReturnForbidden() throws Exception {
        UUID retroId = UUID.randomUUID();

        when(participantService.isFacilitator(any(HttpServletRequest.class), eq(retroId)))
            .thenReturn(false);

        mockMvc.perform(post("/api/retros/{retroId}/timer/pause", retroId)
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void pauseTimer_Unauthenticated_ShouldReturnUnauthorized() throws Exception {
        UUID retroId = UUID.randomUUID();

        mockMvc.perform(post("/api/retros/{retroId}/timer/pause", retroId)
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous())
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    void pauseTimer_WithoutCSRF_ShouldReturnForbidden() throws Exception {
        UUID retroId = UUID.randomUUID();

        mockMvc.perform(post("/api/retros/{retroId}/timer/pause", retroId))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    void resumeTimer_Facilitator_ShouldReturnOk() throws Exception {
        UUID retroId = UUID.randomUUID();

        when(participantService.isFacilitator(any(HttpServletRequest.class), eq(retroId)))
            .thenReturn(true);

        mockMvc.perform(post("/api/retros/{retroId}/timer/resume", retroId)
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    void resumeTimer_NonFacilitator_ShouldReturnForbidden() throws Exception {
        UUID retroId = UUID.randomUUID();

        when(participantService.isFacilitator(any(HttpServletRequest.class), eq(retroId)))
            .thenReturn(false);

        mockMvc.perform(post("/api/retros/{retroId}/timer/resume", retroId)
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void resumeTimer_Unauthenticated_ShouldReturnUnauthorized() throws Exception {
        UUID retroId = UUID.randomUUID();

        mockMvc.perform(post("/api/retros/{retroId}/timer/resume", retroId)
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous())
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    void resumeTimer_WithoutCSRF_ShouldReturnForbidden() throws Exception {
        UUID retroId = UUID.randomUUID();

        mockMvc.perform(post("/api/retros/{retroId}/timer/resume", retroId))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "GUEST")
    void pauseTimer_GuestFacilitator_ShouldReturnOk() throws Exception {
        UUID retroId = UUID.randomUUID();

        when(participantService.isFacilitator(any(HttpServletRequest.class), eq(retroId)))
            .thenReturn(true);

        mockMvc.perform(post("/api/retros/{retroId}/timer/pause", retroId)
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "GUEST")
    void resumeTimer_GuestFacilitator_ShouldReturnOk() throws Exception {
        UUID retroId = UUID.randomUUID();

        when(participantService.isFacilitator(any(HttpServletRequest.class), eq(retroId)))
            .thenReturn(true);

        mockMvc.perform(post("/api/retros/{retroId}/timer/resume", retroId)
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    void getRetroState_ValidParticipant_ShouldIncludeAssistantState() throws Exception {
        UUID retroId = UUID.randomUUID();

        when(participantService.isParticipating(any(HttpServletRequest.class), eq(retroId))).thenReturn(true);

        RetroTemplate mockTemplate = mock(RetroTemplate.class);
        when(mockTemplate.getSetTheStage()).thenReturn((RetroStage) null);
        when(mockTemplate.getGatherData()).thenReturn((RetroStage) null);
        when(mockTemplate.getGenerateInsights()).thenReturn((RetroStage) null);
        when(mockTemplate.getDecideActions()).thenReturn((RetroStage) null);
        when(mockTemplate.getCloseRetro()).thenReturn((RetroStage) null);

        RetroSession mockSession = new RetroSession();
        mockSession.setId(retroId);
        mockSession.setName("Test Session");
        mockSession.setPhase(RetroPhase.SET_THE_STAGE);
        mockSession.setTemplate(mockTemplate);
        mockSession.setCurrentStepIndex(0);

        Participant mockParticipant = new Participant();
        mockParticipant.setParticipantId(UUID.randomUUID());
        mockParticipant.setDisplayName("Test User");
        mockParticipant.setRole(ParticipantRole.FACILITATOR);
        mockParticipant.setSession(mockSession);

        AssistantStateDto assistantState = new AssistantStateDto(null, List.of(), null);

        when(participantService.getParticipantForSession(any(HttpServletRequest.class), eq(retroId)))
            .thenReturn(mockParticipant);
        when(retroSessionService.getSessionById(retroId)).thenReturn(mockSession);
        when(retroSessionService.getCurrentStep(retroId)).thenReturn(null);
        when(participantService.getSessionParticipants(retroId)).thenReturn(List.of(mockParticipant));
        when(retroSessionService.getAssistantHistory(retroId)).thenReturn(assistantState);
        when(retroSyncVersionService.getSyncVersion(retroId)).thenReturn(21L);

        mockMvc.perform(get("/api/retros/{retroId}", retroId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.retroId").value(retroId.toString()))
                .andExpect(jsonPath("$.syncVersion").value(21))
                .andExpect(jsonPath("$.assistantState").exists())
                .andExpect(jsonPath("$.assistantState.history").isArray());
    }

    @Test
    void getRetroState_Unauthenticated_ShouldReturnUnauthorized() throws Exception {
        UUID retroId = UUID.randomUUID();

        mockMvc.perform(get("/api/retros/{retroId}", retroId)
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRequireAuthenticationForSessionCreation() throws Exception {
        mockMvc.perform(post("/api/retros")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous()) 
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionName\":\"Test Session\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    void shouldRequireCSRFToken() throws Exception {
        mockMvc.perform(post("/api/retros")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionName\":\"Test Session\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void guestAuth_ValidDisplayName_ShouldRedirectToHome() throws Exception {
        mockMvc.perform(post("/auth/guest")
                .param("displayName", "Test Guest User")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void guestAuth_EmptyDisplayName_ShouldRedirectToLoginWithError() throws Exception {
        mockMvc.perform(post("/auth/guest")
                .param("displayName", "")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login?error=missing_display_name"));
    }
}
