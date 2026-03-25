package direct.reflect.facilitator.facilitation;

import org.junit.jupiter.api.BeforeEach;
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

import direct.reflect.facilitator.facilitation.RetroSession;
import direct.reflect.facilitator.facilitation.Participant;
import direct.reflect.facilitator.facilitation.ParticipantRole;
import direct.reflect.facilitator.facilitation.ParticipantService;
import direct.reflect.facilitator.facilitation.RetroSessionService;
import direct.reflect.facilitator.eventing.EventService;
import direct.reflect.facilitator.facilitation.RetroApiController;
import direct.reflect.facilitator.facilitation.dto.CreateRetroRequest;
import direct.reflect.facilitator.facilitation.dto.JoinRetroRequest;
import direct.reflect.facilitator.facilitation.dto.AssistantStateDto;
import direct.reflect.facilitator.facilitation.response.ResponseService;
import direct.reflect.facilitator.facilitation.response.ParticipantResponse;
import direct.reflect.facilitator.configurator.RetroStep;
import direct.reflect.facilitator.configurator.RetroTemplate;
import direct.reflect.facilitator.common.exception.InputLimitExceededException;
import direct.reflect.facilitator.common.exception.ParticipantNotFoundException;
import direct.reflect.facilitator.facilitation.dto.TimerStateDto;
import direct.reflect.facilitator.auth.AuthService;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;

@WebMvcTest(controllers = {
    RetroApiController.class, 
    direct.reflect.facilitator.auth.AuthController.class
})
@Import({
    direct.reflect.facilitator.config.TestSecurityOverride.class,
    direct.reflect.facilitator.auth.AuthService.class
})
@EnableAutoConfiguration(exclude = {
    DataSourceAutoConfiguration.class,
    HibernateJpaAutoConfiguration.class
})
@org.springframework.test.context.ActiveProfiles("test")
public class RetroApiControllerTest {

    @Autowired
    private MockMvc mockMvc;
    
    @MockitoBean 
    private RetroSessionService retroSessionService;
    
    @MockitoBean 
    private ParticipantService participantService;
    
    @MockitoBean 
    private EventService eventService;
    
    @MockitoBean
    private ResponseService responseService;

    @MockitoBean
    private AuthService authHelper;

    @MockitoBean
    private direct.reflect.facilitator.configurator.RetroStepRepository stepRepository;

    @BeforeEach
    void setUpDefaultMocks() {
        ParticipantResponse defaultResponse = new ParticipantResponse();
        defaultResponse.setId(UUID.randomUUID());
        RetroStep defaultStep = new RetroStep();
        defaultStep.setId(1L);
        defaultResponse.setRetroStep(defaultStep);
        when(responseService.submitResponse(any(), any(), any(), any(HttpServletRequest.class)))
            .thenReturn(defaultResponse);
    }

    @Test
    @WithMockUser(roles = "USER")
    void shouldCreateRetrospectiveAndReturnJson() throws Exception {
        // Arrange
        UUID retroId = UUID.randomUUID();
        RetroSession mockSession = new RetroSession();
        mockSession.setId(retroId); 
        mockSession.setName("Test Session");
        
        Participant mockParticipant = new Participant();
        mockParticipant.setParticipantId(UUID.randomUUID());
        mockParticipant.setDisplayName("Test User");
        mockParticipant.setSession(mockSession);

        CreateRetroRequest request = new CreateRetroRequest("Test Session");

        when(retroSessionService.createSessionWithFacilitator(eq("Test Session"), any(HttpServletRequest.class)))
            .thenReturn(mockSession);

        // Act & Assert
        mockMvc.perform(post("/api/retro/create")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionName\":\"Test Session\"}"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.retroId").value(retroId.toString()))
                .andExpect(jsonPath("$.redirectUrl").value("/retro/" + retroId))
                .andExpect(jsonPath("$.sessionName").value("Test Session"));
    }
    
    @Test
    @WithMockUser(roles = "GUEST")
    void shouldJoinRetrospectiveAndReturnJson() throws Exception {
        // Arrange
        UUID retroId = UUID.randomUUID();
        JoinRetroRequest request = new JoinRetroRequest(retroId.toString());

        RetroSession mockSession = new RetroSession();
        mockSession.setId(retroId);
        mockSession.setName("Existing Session");

        Participant mockParticipant = new Participant();
        mockParticipant.setParticipantId(UUID.randomUUID()); 
        mockParticipant.setDisplayName("Guest");
        mockParticipant.setSession(mockSession);

        when(retroSessionService.sessionExists(retroId)).thenReturn(true);
        when(retroSessionService.getSessionById(retroId)).thenReturn(mockSession); 
        when(participantService.addParticipantToSession(any(HttpServletRequest.class), eq(mockSession), eq(ParticipantRole.PARTICIPANT)))
            .thenReturn(mockParticipant);
        doNothing().when(eventService).publish(any());

        // Act & Assert
        mockMvc.perform(post("/api/retro/join")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"retroId\":\"" + retroId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.retroId").value(retroId.toString()))
                .andExpect(jsonPath("$.redirectUrl").value("/retro/" + retroId));
    }
    
    @Test
    @WithMockUser(roles = "GUEST")
    void shouldAllowGuestUserCreatingSession() throws Exception {
        // Arrange
        CreateRetroRequest request = new CreateRetroRequest("Test Session");
        
        UUID retroId = UUID.randomUUID();
        RetroSession mockSession = new RetroSession();
        mockSession.setId(retroId);
        mockSession.setName("Test Session");
        
        Participant mockParticipant = new Participant();
        mockParticipant.setParticipantId(UUID.randomUUID());
        mockParticipant.setDisplayName("Test Guest");
        mockParticipant.setSession(mockSession);

        when(retroSessionService.createSessionWithFacilitator(eq("Test Session"), any(HttpServletRequest.class)))
            .thenReturn(mockSession);

        // Act & Assert
        mockMvc.perform(post("/api/retro/create")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionName\":\"Test Session\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.retroId").value(retroId.toString()))
                .andExpect(jsonPath("$.redirectUrl").value("/retro/" + retroId));
    }
    
    @Test
    void shouldRequireAuthenticationForSessionCreation() throws Exception {
        // Arrange
        CreateRetroRequest request = new CreateRetroRequest("Test Session");

        // Act & Assert - Test unauthenticated access
        mockMvc.perform(post("/api/retro/create")
                .with(anonymous()) // Explicitly ensure no authentication
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionName\":\"Test Session\"}"))
                .andExpect(status().isUnauthorized()); // Should return 401 for API endpoints with OAuth
    }
    
    @Test
    @WithMockUser(roles = "USER")
    void shouldRequireCSRFToken() throws Exception {
        // Arrange
        CreateRetroRequest request = new CreateRetroRequest("Test Session");

        // Act & Assert - Test without CSRF token (should be rejected)
        mockMvc.perform(post("/api/retro/create")
                // No CSRF token provided
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionName\":\"Test Session\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void guestAuth_ValidDisplayName_ShouldRedirectToHome() throws Exception {
        // Guest authentication should accept POST with displayName and redirect to home
        mockMvc.perform(post("/auth/guest")
                .param("displayName", "Test Guest User")
                .with(csrf()))
                .andExpect(status().isFound()) // Guest auth redirects to /
                .andExpect(redirectedUrl("/"));
    }
    
    @Test
    void guestAuth_EmptyDisplayName_ShouldRedirectToLoginWithError() throws Exception {
        // Guest authentication should reject empty display name
        mockMvc.perform(post("/auth/guest")
                .param("displayName", "")
                .with(csrf()))
                .andExpect(status().isFound()) // Guest auth redirects on error
                .andExpect(redirectedUrl("/login?error=missing_display_name"));
    }

    // ============================================================================
    // Response Submission Tests
    // ============================================================================

    @Test
    @WithMockUser(roles = "USER")
    void submitColumnResponse_ValidInput_ShouldReturnOk() throws Exception {
        // Arrange
        UUID retroId = UUID.randomUUID();
        Long stepId = 1L;

        // Act & Assert
        mockMvc.perform(post("/api/retro/{retroId}/step/{stepId}/response/column", retroId, stepId)
                .with(csrf())
                .param("columnId", "Mad")
                .param("content", "Too many meetings"))
                .andExpect(status().isOk())
                .andExpect(header().string("HX-Trigger", "responseSubmitted"));
    }

    @Test
    @WithMockUser(roles = "GUEST")
    void submitColumnResponse_GuestUser_ShouldReturnOk() throws Exception {
        // Arrange
        UUID retroId = UUID.randomUUID();
        Long stepId = 1L;

        // Act & Assert
        mockMvc.perform(post("/api/retro/{retroId}/step/{stepId}/response/column", retroId, stepId)
                .with(csrf())
                .param("columnId", "Glad")
                .param("content", "Great collaboration"))
                .andExpect(status().isOk())
                .andExpect(header().string("HX-Trigger", "responseSubmitted"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void submitColumnResponse_EmptyContent_ShouldReturnBadRequest() throws Exception {
        // Arrange
        UUID retroId = UUID.randomUUID();
        Long stepId = 1L;

        // Act & Assert - Empty content violates @NotBlank constraint
        mockMvc.perform(post("/api/retro/{retroId}/step/{stepId}/response/column", retroId, stepId)
                .with(csrf())
                .param("columnId", "Mad")
                .param("content", ""))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Validation failed")));
    }

    @Test
    @WithMockUser(roles = "USER")
    void submitColumnResponse_ContentTooLong_ShouldReturnBadRequest() throws Exception {
        // Arrange
        UUID retroId = UUID.randomUUID();
        Long stepId = 1L;
        String tooLongContent = "a".repeat(501); // Exceeds @Size(max = 500)

        // Act & Assert
        mockMvc.perform(post("/api/retro/{retroId}/step/{stepId}/response/column", retroId, stepId)
                .with(csrf())
                .param("columnId", "Mad")
                .param("content", tooLongContent))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Validation failed")));
    }

    @Test
    @WithMockUser(roles = "USER")
    void submitColumnResponse_MissingColumnId_ShouldReturnBadRequest() throws Exception {
        // Arrange
        UUID retroId = UUID.randomUUID();
        Long stepId = 1L;

        // Act & Assert - Missing columnId violates @NotBlank constraint
        mockMvc.perform(post("/api/retro/{retroId}/step/{stepId}/response/column", retroId, stepId)
                .with(csrf())
                .param("content", "Some content"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "USER")
    void submitRatingResponse_ValidInput_ShouldReturnOk() throws Exception {
        // Arrange
        UUID retroId = UUID.randomUUID();
        Long stepId = 1L;

        // Act & Assert
        mockMvc.perform(post("/api/retro/{retroId}/step/{stepId}/response/rating", retroId, stepId)
                .with(csrf())
                .param("rating", "8")
                .param("comment", "Good sprint overall"))
                .andExpect(status().isOk())
                .andExpect(header().string("HX-Trigger", "responseSubmitted"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void submitRatingResponse_WithoutComment_ShouldReturnOk() throws Exception {
        // Arrange
        UUID retroId = UUID.randomUUID();
        Long stepId = 1L;

        // Act & Assert - Comment is optional
        mockMvc.perform(post("/api/retro/{retroId}/step/{stepId}/response/rating", retroId, stepId)
                .with(csrf())
                .param("rating", "7"))
                .andExpect(status().isOk())
                .andExpect(header().string("HX-Trigger", "responseSubmitted"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void submitRatingResponse_RatingTooLow_ShouldReturnBadRequest() throws Exception {
        // Arrange
        UUID retroId = UUID.randomUUID();
        Long stepId = 1L;

        // Act & Assert - Rating 0 violates @Min(1) constraint
        mockMvc.perform(post("/api/retro/{retroId}/step/{stepId}/response/rating", retroId, stepId)
                .with(csrf())
                .param("rating", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Validation failed")));
    }

    @Test
    @WithMockUser(roles = "USER")
    void submitRatingResponse_RatingTooHigh_ShouldReturnBadRequest() throws Exception {
        // Arrange
        UUID retroId = UUID.randomUUID();
        Long stepId = 1L;

        // Act & Assert - Rating 11 violates @Max(10) constraint
        mockMvc.perform(post("/api/retro/{retroId}/step/{stepId}/response/rating", retroId, stepId)
                .with(csrf())
                .param("rating", "11"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Validation failed")));
    }

    @Test
    @WithMockUser(roles = "USER")
    void submitRatingResponse_MissingRating_ShouldReturnBadRequest() throws Exception {
        // Arrange
        UUID retroId = UUID.randomUUID();
        Long stepId = 1L;

        // Act & Assert - Missing rating violates @NotNull constraint
        mockMvc.perform(post("/api/retro/{retroId}/step/{stepId}/response/rating", retroId, stepId)
                .with(csrf())
                .param("comment", "Some comment"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void submitColumnResponse_Unauthenticated_ShouldReturnUnauthorized() throws Exception {
        // Arrange
        UUID retroId = UUID.randomUUID();
        Long stepId = 1L;

        // Act & Assert - Unauthenticated requests should be rejected
        mockMvc.perform(post("/api/retro/{retroId}/step/{stepId}/response/column", retroId, stepId)
                .with(anonymous())
                .with(csrf())
                .param("columnId", "Mad")
                .param("content", "Should not work"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void submitRatingResponse_Unauthenticated_ShouldReturnUnauthorized() throws Exception {
        // Arrange
        UUID retroId = UUID.randomUUID();
        Long stepId = 1L;

        // Act & Assert - Unauthenticated requests should be rejected
        mockMvc.perform(post("/api/retro/{retroId}/step/{stepId}/response/rating", retroId, stepId)
                .with(anonymous())
                .with(csrf())
                .param("rating", "8"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    void submitColumnResponse_WithoutCSRF_ShouldReturnForbidden() throws Exception {
        // Arrange
        UUID retroId = UUID.randomUUID();
        Long stepId = 1L;

        // Act & Assert - Requests without CSRF token should be rejected
        mockMvc.perform(post("/api/retro/{retroId}/step/{stepId}/response/column", retroId, stepId)
                .param("columnId", "Mad")
                .param("content", "Should not work"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    void submitRatingResponse_WithoutCSRF_ShouldReturnForbidden() throws Exception {
        // Arrange
        UUID retroId = UUID.randomUUID();
        Long stepId = 1L;

        // Act & Assert - Requests without CSRF token should be rejected
        mockMvc.perform(post("/api/retro/{retroId}/step/{stepId}/response/rating", retroId, stepId)
                .param("rating", "8"))
                .andExpect(status().isForbidden());
    }

    // ============================================================================
    // Input Limit Tests
    // ============================================================================

    @Test
    @WithMockUser(roles = "USER")
    void submitColumnResponse_InputLimitExceeded_ShouldReturnBadRequest() throws Exception {
        // Arrange
        UUID retroId = UUID.randomUUID();
        Long stepId = 1L;
        
        when(responseService.submitResponse(eq(retroId), eq(stepId), any(), any(HttpServletRequest.class)))
            .thenThrow(new InputLimitExceededException(10, 10));

        // Act & Assert
        mockMvc.perform(post("/api/retro/{retroId}/step/{stepId}/response/column", retroId, stepId)
                .with(csrf())
                .param("columnId", "Mad")
                .param("content", "This is my 11th input"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "USER")
    void submitColumnResponse_UnderInputLimit_ShouldReturnOk() throws Exception {
        // Arrange
        UUID retroId = UUID.randomUUID();
        Long stepId = 1L;
        
        ParticipantResponse mockResponse = new ParticipantResponse();
        mockResponse.setId(UUID.randomUUID());
        RetroStep mockStep = new RetroStep();
        mockStep.setId(stepId);
        mockResponse.setRetroStep(mockStep);
        when(responseService.submitResponse(eq(retroId), eq(stepId), any(), any(HttpServletRequest.class)))
            .thenReturn(mockResponse);

        // Act & Assert
        mockMvc.perform(post("/api/retro/{retroId}/step/{stepId}/response/column", retroId, stepId)
                .with(csrf())
                .param("columnId", "Mad")
                .param("content", "Valid input under limit"))
                .andExpect(status().isOk())
                .andExpect(header().string("HX-Trigger", "responseSubmitted"));
    }

    // ============================================================================
    // Timer API Tests
    // ============================================================================

    @Test
    @WithMockUser(roles = "USER")
    void getTimerState_ValidParticipant_ShouldReturnTimerState() throws Exception {
        // Arrange
        UUID retroId = UUID.randomUUID();
        TimerStateDto timerState = new TimerStateDto(300, false, "green");
        
        when(retroSessionService.getTimerState(retroId)).thenReturn(timerState);

        // Act & Assert
        mockMvc.perform(get("/api/retro/{retroId}/timer", retroId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remainingSeconds").value(300))
                .andExpect(jsonPath("$.isPaused").value(false))
                .andExpect(jsonPath("$.state").value("green"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getTimerState_NoTimerForStep_ShouldReturnNoContent() throws Exception {
        // Arrange
        UUID retroId = UUID.randomUUID();
        
        when(retroSessionService.getTimerState(retroId)).thenReturn(null);

        // Act & Assert
        mockMvc.perform(get("/api/retro/{retroId}/timer", retroId))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "USER")
    void getTimerState_NotParticipant_ShouldReturnForbidden() throws Exception {
        // Arrange
        UUID retroId = UUID.randomUUID();
        
        when(participantService.getParticipantForSession(any(HttpServletRequest.class), eq(retroId)))
            .thenThrow(new ParticipantNotFoundException("Not a participant"));

        // Act & Assert
        mockMvc.perform(get("/api/retro/{retroId}/timer", retroId))
                .andExpect(status().isForbidden());
    }

    @Test
    void getTimerState_Unauthenticated_ShouldReturnUnauthorized() throws Exception {
        // Arrange
        UUID retroId = UUID.randomUUID();

        // Act & Assert
        mockMvc.perform(get("/api/retro/{retroId}/timer", retroId)
                .with(anonymous()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    void pauseTimer_Facilitator_ShouldReturnOk() throws Exception {
        // Arrange
        UUID retroId = UUID.randomUUID();
        
        when(participantService.isFacilitator(any(HttpServletRequest.class), eq(retroId)))
            .thenReturn(true);

        // Act & Assert
        mockMvc.perform(post("/api/retro/{retroId}/timer/pause", retroId)
                .with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    void pauseTimer_NonFacilitator_ShouldReturnForbidden() throws Exception {
        // Arrange
        UUID retroId = UUID.randomUUID();
        
        when(participantService.isFacilitator(any(HttpServletRequest.class), eq(retroId)))
            .thenReturn(false);

        // Act & Assert
        mockMvc.perform(post("/api/retro/{retroId}/timer/pause", retroId)
                .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void pauseTimer_Unauthenticated_ShouldReturnUnauthorized() throws Exception {
        // Arrange
        UUID retroId = UUID.randomUUID();

        // Act & Assert
        mockMvc.perform(post("/api/retro/{retroId}/timer/pause", retroId)
                .with(anonymous())
                .with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    void pauseTimer_WithoutCSRF_ShouldReturnForbidden() throws Exception {
        // Arrange
        UUID retroId = UUID.randomUUID();

        // Act & Assert
        mockMvc.perform(post("/api/retro/{retroId}/timer/pause", retroId))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    void resumeTimer_Facilitator_ShouldReturnOk() throws Exception {
        // Arrange
        UUID retroId = UUID.randomUUID();
        
        when(participantService.isFacilitator(any(HttpServletRequest.class), eq(retroId)))
            .thenReturn(true);

        // Act & Assert
        mockMvc.perform(post("/api/retro/{retroId}/timer/resume", retroId)
                .with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    void resumeTimer_NonFacilitator_ShouldReturnForbidden() throws Exception {
        // Arrange
        UUID retroId = UUID.randomUUID();
        
        when(participantService.isFacilitator(any(HttpServletRequest.class), eq(retroId)))
            .thenReturn(false);

        // Act & Assert
        mockMvc.perform(post("/api/retro/{retroId}/timer/resume", retroId)
                .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void resumeTimer_Unauthenticated_ShouldReturnUnauthorized() throws Exception {
        // Arrange
        UUID retroId = UUID.randomUUID();

        // Act & Assert
        mockMvc.perform(post("/api/retro/{retroId}/timer/resume", retroId)
                .with(anonymous())
                .with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    void resumeTimer_WithoutCSRF_ShouldReturnForbidden() throws Exception {
        // Arrange
        UUID retroId = UUID.randomUUID();

        // Act & Assert
        mockMvc.perform(post("/api/retro/{retroId}/timer/resume", retroId))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "GUEST")
    void pauseTimer_GuestFacilitator_ShouldReturnOk() throws Exception {
        // Arrange
        UUID retroId = UUID.randomUUID();
        
        when(participantService.isFacilitator(any(HttpServletRequest.class), eq(retroId)))
            .thenReturn(true);

        // Act & Assert - Guests can be facilitators
        mockMvc.perform(post("/api/retro/{retroId}/timer/pause", retroId)
                .with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "GUEST")
    void resumeTimer_GuestFacilitator_ShouldReturnOk() throws Exception {
        // Arrange
        UUID retroId = UUID.randomUUID();
        
        when(participantService.isFacilitator(any(HttpServletRequest.class), eq(retroId)))
            .thenReturn(true);

        // Act & Assert - Guests can be facilitators
        mockMvc.perform(post("/api/retro/{retroId}/timer/resume", retroId)
                .with(csrf()))
                .andExpect(status().isOk());
    }

    // ============================================================================
    // JSON Response Body Tests
    // ============================================================================

    @Test
    @WithMockUser(roles = "USER")
    void nextStep_Facilitator_ShouldReturnJsonWithAdvancedTrue() throws Exception {
        UUID retroId = UUID.randomUUID();

        when(participantService.isFacilitator(any(HttpServletRequest.class), eq(retroId)))
            .thenReturn(true);

        mockMvc.perform(post("/api/retro/{retroId}/next", retroId)
                .with(csrf()))
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

        mockMvc.perform(post("/api/retro/{retroId}/next", retroId)
                .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    void leaveActiveSessions_ShouldReturnJsonWithSuccessTrue() throws Exception {
        mockMvc.perform(post("/api/retro/leave-active-sessions")
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(roles = "USER")
    void submitColumnResponse_ShouldReturnJsonWithResponseId() throws Exception {
        UUID retroId = UUID.randomUUID();
        Long stepId = 1L;
        UUID responseId = UUID.randomUUID();

        ParticipantResponse mockResponse = new ParticipantResponse();
        mockResponse.setId(responseId);
        RetroStep mockStep = new RetroStep();
        mockStep.setId(stepId);
        mockResponse.setRetroStep(mockStep);

        when(responseService.submitResponse(eq(retroId), eq(stepId), any(), any(HttpServletRequest.class)))
            .thenReturn(mockResponse);

        mockMvc.perform(post("/api/retro/{retroId}/step/{stepId}/response/column", retroId, stepId)
                .with(csrf())
                .param("columnId", "Mad")
                .param("content", "Valid response"))
                .andExpect(status().isOk())
                .andExpect(header().string("HX-Trigger", "responseSubmitted"))
                .andExpect(jsonPath("$.responseId").value(responseId.toString()))
                .andExpect(jsonPath("$.stepId").value(stepId));
    }

    @Test
    @WithMockUser(roles = "USER")
    void submitRatingResponse_ShouldReturnJsonWithResponseId() throws Exception {
        UUID retroId = UUID.randomUUID();
        Long stepId = 1L;
        UUID responseId = UUID.randomUUID();

        ParticipantResponse mockResponse = new ParticipantResponse();
        mockResponse.setId(responseId);
        RetroStep mockStep = new RetroStep();
        mockStep.setId(stepId);
        mockResponse.setRetroStep(mockStep);

        when(responseService.submitResponse(eq(retroId), eq(stepId), any(), any(HttpServletRequest.class)))
            .thenReturn(mockResponse);

        mockMvc.perform(post("/api/retro/{retroId}/step/{stepId}/response/rating", retroId, stepId)
                .with(csrf())
                .param("rating", "7"))
                .andExpect(status().isOk())
                .andExpect(header().string("HX-Trigger", "responseSubmitted"))
                .andExpect(jsonPath("$.responseId").value(responseId.toString()))
                .andExpect(jsonPath("$.stepId").value(stepId));
    }

    @Test
    @WithMockUser(roles = "USER")
    void toggleVote_ShouldReturnJsonWithVoteCount() throws Exception {
        UUID retroId = UUID.randomUUID();
        UUID responseId = UUID.randomUUID();

        ParticipantResponse mockResponse = new ParticipantResponse();
        mockResponse.setId(responseId);
        mockResponse.getResponseData().put("votes", java.util.List.of("participant-1", "participant-2"));

        when(responseService.toggleVote(eq(retroId), eq(responseId), any(HttpServletRequest.class)))
            .thenReturn(mockResponse);

        mockMvc.perform(post("/api/retro/{retroId}/response/{responseId}/vote", retroId, responseId)
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(header().string("HX-Trigger", "voteToggled"))
                .andExpect(jsonPath("$.responseId").value(responseId.toString()))
                .andExpect(jsonPath("$.voteCount").value(2));
    }

    @Test
    @WithMockUser(roles = "USER")
    void revealResponses_Facilitator_ShouldReturnJsonRevealResult() throws Exception {
        UUID retroId = UUID.randomUUID();
        Long stepId = 1L;

        RetroSession mockSession = new RetroSession();
        mockSession.setId(retroId);
        mockSession.setName("Test Session");

        when(participantService.isFacilitator(any(HttpServletRequest.class), eq(retroId)))
            .thenReturn(true);
        when(retroSessionService.getSessionById(retroId)).thenReturn(mockSession);

        mockMvc.perform(post("/api/retro/{retroId}/step/{stepId}/reveal", retroId, stepId)
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(header().string("HX-Trigger", "responsesRevealed"))
                .andExpect(jsonPath("$.stepId").value(stepId))
                .andExpect(jsonPath("$.revealed").value(true));
    }

    @Test
    @WithMockUser(roles = "USER")
    void revealResponses_NonFacilitator_ShouldReturnForbidden() throws Exception {
        UUID retroId = UUID.randomUUID();
        Long stepId = 1L;

        when(participantService.isFacilitator(any(HttpServletRequest.class), eq(retroId)))
            .thenReturn(false);

        mockMvc.perform(post("/api/retro/{retroId}/step/{stepId}/reveal", retroId, stepId)
                .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    void updateResponse_ValidParticipant_ShouldReturnJsonUpdateResult() throws Exception {
        UUID retroId = UUID.randomUUID();
        UUID responseId = UUID.randomUUID();
        String updatedContent = "Updated content";

        Participant mockParticipant = new Participant();
        mockParticipant.setParticipantId(UUID.randomUUID());
        mockParticipant.setDisplayName("Test User");

        ParticipantResponse mockResponse = new ParticipantResponse();
        mockResponse.setId(responseId);

        when(participantService.getParticipantForSession(any(HttpServletRequest.class), eq(retroId)))
            .thenReturn(mockParticipant);
        when(responseService.updateResponse(eq(responseId), eq(mockParticipant), eq(updatedContent)))
            .thenReturn(mockResponse);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put("/api/retro/{retroId}/response/{responseId}", retroId, responseId)
                .with(csrf())
                .param("content", updatedContent))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseId").value(responseId.toString()))
                .andExpect(jsonPath("$.content").value(updatedContent));
    }

    // ============================================================================
    // Retro State API Tests
    // ============================================================================

    @Test
    @WithMockUser(roles = "USER")
    void getRetroState_ValidParticipant_ShouldIncludeAssistantState() throws Exception {
        UUID retroId = UUID.randomUUID();

        RetroTemplate mockTemplate = org.mockito.Mockito.mock(RetroTemplate.class);
        when(mockTemplate.getStageForPhase(any())).thenReturn(null);

        RetroSession mockSession = new RetroSession();
        mockSession.setId(retroId);
        mockSession.setName("Test Session");
        mockSession.setPhase(direct.reflect.facilitator.facilitation.RetroPhase.SET_THE_STAGE);
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

        mockMvc.perform(get("/api/retro/{retroId}/state", retroId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.retroId").value(retroId.toString()))
                .andExpect(jsonPath("$.assistantState").exists())
                .andExpect(jsonPath("$.assistantState.history").isArray());
    }

    @Test
    void getRetroState_Unauthenticated_ShouldReturnUnauthorized() throws Exception {
        UUID retroId = UUID.randomUUID();

        mockMvc.perform(get("/api/retro/{retroId}/state", retroId)
                .with(anonymous()))
                .andExpect(status().isUnauthorized());
    }
}