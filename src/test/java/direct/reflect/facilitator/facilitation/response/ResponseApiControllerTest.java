package direct.reflect.facilitator.facilitation.response;

import direct.reflect.facilitator.facilitation.participant.Participant;
import direct.reflect.facilitator.facilitation.participant.ParticipantService;
import direct.reflect.facilitator.facilitation.session.RetroSessionService;
import direct.reflect.facilitator.facilitation.session.RetroSession;
import direct.reflect.facilitator.auth.AuthService;
import direct.reflect.facilitator.configurator.RetroStep;
import direct.reflect.facilitator.configurator.RetroStage;
import direct.reflect.facilitator.configurator.RetroStepQueryService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import jakarta.servlet.http.HttpServletRequest;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
    ResponseApiController.class,
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
public class ResponseApiControllerTest {

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
    private direct.reflect.facilitator.facilitation.session.RetroSyncVersionService retroSyncVersionService;

    @MockitoBean
    private direct.reflect.facilitator.eventing.EventService eventService;

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
    void submitColumnResponse_ValidInput_ShouldReturnOk() throws Exception {
        UUID retroId = UUID.randomUUID();
        Long stepId = 1L;

        mockMvc.perform(post("/api/retros/{retroId}/steps/{stepId}/responses/column", retroId, stepId)
                .with(SecurityMockMvcRequestPostProcessors.csrf())
                .param("columnId", "Mad")
                .param("content", "Too many meetings"))
                .andExpect(status().isOk())
                .andExpect(header().string("HX-Trigger", "responseSubmitted"));
    }

    @Test
    @WithMockUser(roles = "GUEST")
    void submitColumnResponse_GuestUser_ShouldReturnOk() throws Exception {
        UUID retroId = UUID.randomUUID();
        Long stepId = 1L;

        mockMvc.perform(post("/api/retros/{retroId}/steps/{stepId}/responses/column", retroId, stepId)
                .with(SecurityMockMvcRequestPostProcessors.csrf())
                .param("columnId", "Glad")
                .param("content", "Great collaboration"))
                .andExpect(status().isOk())
                .andExpect(header().string("HX-Trigger", "responseSubmitted"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void submitColumnResponse_EmptyContent_ShouldReturnBadRequest() throws Exception {
        UUID retroId = UUID.randomUUID();
        Long stepId = 1L;

        mockMvc.perform(post("/api/retros/{retroId}/steps/{stepId}/responses/column", retroId, stepId)
                .with(SecurityMockMvcRequestPostProcessors.csrf())
                .param("columnId", "Mad")
                .param("content", ""))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Validation failed")));
    }

    @Test
    @WithMockUser(roles = "USER")
    void submitColumnResponse_ContentTooLong_ShouldReturnBadRequest() throws Exception {
        UUID retroId = UUID.randomUUID();
        Long stepId = 1L;
        String tooLongContent = "a".repeat(501);

        mockMvc.perform(post("/api/retros/{retroId}/steps/{stepId}/responses/column", retroId, stepId)
                .with(SecurityMockMvcRequestPostProcessors.csrf())
                .param("columnId", "Mad")
                .param("content", tooLongContent))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Validation failed")));
    }

    @Test
    @WithMockUser(roles = "USER")
    void submitColumnResponse_MissingColumnId_ShouldReturnBadRequest() throws Exception {
        UUID retroId = UUID.randomUUID();
        Long stepId = 1L;

        mockMvc.perform(post("/api/retros/{retroId}/steps/{stepId}/responses/column", retroId, stepId)
                .with(SecurityMockMvcRequestPostProcessors.csrf())
                .param("content", "Some content"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "USER")
    void submitRatingResponse_ValidInput_ShouldReturnOk() throws Exception {
        UUID retroId = UUID.randomUUID();
        Long stepId = 1L;

        mockMvc.perform(post("/api/retros/{retroId}/steps/{stepId}/responses/rating", retroId, stepId)
                .with(SecurityMockMvcRequestPostProcessors.csrf())
                .param("rating", "8")
                .param("comment", "Good sprint overall"))
                .andExpect(status().isOk())
                .andExpect(header().string("HX-Trigger", "responseSubmitted"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void submitRatingResponse_WithoutComment_ShouldReturnOk() throws Exception {
        UUID retroId = UUID.randomUUID();
        Long stepId = 1L;

        mockMvc.perform(post("/api/retros/{retroId}/steps/{stepId}/responses/rating", retroId, stepId)
                .with(SecurityMockMvcRequestPostProcessors.csrf())
                .param("rating", "7"))
                .andExpect(status().isOk())
                .andExpect(header().string("HX-Trigger", "responseSubmitted"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void submitRatingResponse_RatingTooLow_ShouldReturnBadRequest() throws Exception {
        UUID retroId = UUID.randomUUID();
        Long stepId = 1L;

        mockMvc.perform(post("/api/retros/{retroId}/steps/{stepId}/responses/rating", retroId, stepId)
                .with(SecurityMockMvcRequestPostProcessors.csrf())
                .param("rating", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Validation failed")));
    }

    @Test
    @WithMockUser(roles = "USER")
    void submitRatingResponse_RatingTooHigh_ShouldReturnBadRequest() throws Exception {
        UUID retroId = UUID.randomUUID();
        Long stepId = 1L;

        mockMvc.perform(post("/api/retros/{retroId}/steps/{stepId}/responses/rating", retroId, stepId)
                .with(SecurityMockMvcRequestPostProcessors.csrf())
                .param("rating", "11"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Validation failed")));
    }

    @Test
    @WithMockUser(roles = "USER")
    void submitRatingResponse_MissingRating_ShouldReturnBadRequest() throws Exception {
        UUID retroId = UUID.randomUUID();
        Long stepId = 1L;

        mockMvc.perform(post("/api/retros/{retroId}/steps/{stepId}/responses/rating", retroId, stepId)
                .with(SecurityMockMvcRequestPostProcessors.csrf())
                .param("comment", "Some comment"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void submitColumnResponse_Unauthenticated_ShouldReturnUnauthorized() throws Exception {
        UUID retroId = UUID.randomUUID();
        Long stepId = 1L;

        mockMvc.perform(post("/api/retros/{retroId}/steps/{stepId}/responses/column", retroId, stepId)
                .with(SecurityMockMvcRequestPostProcessors.anonymous())
                .with(SecurityMockMvcRequestPostProcessors.csrf())
                .param("columnId", "Mad")
                .param("content", "Should not work"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void submitRatingResponse_Unauthenticated_ShouldReturnUnauthorized() throws Exception {
        UUID retroId = UUID.randomUUID();
        Long stepId = 1L;

        mockMvc.perform(post("/api/retros/{retroId}/steps/{stepId}/responses/rating", retroId, stepId)
                .with(SecurityMockMvcRequestPostProcessors.anonymous())
                .with(SecurityMockMvcRequestPostProcessors.csrf())
                .param("rating", "8"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    void submitColumnResponse_WithoutCSRF_ShouldReturnForbidden() throws Exception {
        UUID retroId = UUID.randomUUID();
        Long stepId = 1L;

        mockMvc.perform(post("/api/retros/{retroId}/steps/{stepId}/responses/column", retroId, stepId)
                .param("columnId", "Mad")
                .param("content", "Should not work"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    void submitRatingResponse_WithoutCSRF_ShouldReturnForbidden() throws Exception {
        UUID retroId = UUID.randomUUID();
        Long stepId = 1L;

        mockMvc.perform(post("/api/retros/{retroId}/steps/{stepId}/responses/rating", retroId, stepId)
                .param("rating", "8"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    void submitColumnResponse_InputLimitExceeded_ShouldReturnBadRequest() throws Exception {
        UUID retroId = UUID.randomUUID();
        Long stepId = 1L;

        when(responseService.submitResponse(eq(retroId), eq(stepId), any(), any(HttpServletRequest.class)))
            .thenThrow(new InputLimitExceededException(10, 10));

        mockMvc.perform(post("/api/retros/{retroId}/steps/{stepId}/responses/column", retroId, stepId)
                .with(SecurityMockMvcRequestPostProcessors.csrf())
                .param("columnId", "Mad")
                .param("content", "This is my 11th input"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "USER")
    void submitColumnResponse_UnderInputLimit_ShouldReturnOk() throws Exception {
        UUID retroId = UUID.randomUUID();
        Long stepId = 1L;
        ParticipantResponse mockResponse = new ParticipantResponse();
        mockResponse.setId(UUID.randomUUID());
        RetroStep mockStep = new RetroStep();
        mockStep.setId(stepId);
        mockResponse.setRetroStep(mockStep);

        when(responseService.submitResponse(eq(retroId), eq(stepId), any(), any(HttpServletRequest.class)))
            .thenReturn(mockResponse);

        mockMvc.perform(post("/api/retros/{retroId}/steps/{stepId}/responses/column", retroId, stepId)
                .with(SecurityMockMvcRequestPostProcessors.csrf())
                .param("columnId", "Mad")
                .param("content", "Valid input under limit"))
                .andExpect(status().isOk())
                .andExpect(header().string("HX-Trigger", "responseSubmitted"));
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

        mockMvc.perform(post("/api/retros/{retroId}/steps/{stepId}/responses/column", retroId, stepId)
                .with(SecurityMockMvcRequestPostProcessors.csrf())
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

        mockMvc.perform(post("/api/retros/{retroId}/steps/{stepId}/responses/rating", retroId, stepId)
                .with(SecurityMockMvcRequestPostProcessors.csrf())
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

        mockMvc.perform(post("/api/retros/{retroId}/responses/{responseId}/vote", retroId, responseId)
                .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk())
                .andExpect(header().string("HX-Trigger", "voteToggled"))
                .andExpect(jsonPath("$.responseId").value(responseId.toString()))
                .andExpect(jsonPath("$.voteCount").value(2));
    }

    @Test
    @WithMockUser(roles = "USER")
    void updateResponse_ValidParticipant_ShouldReturnJsonUpdateResult() throws Exception {
        UUID retroId = UUID.randomUUID();
        UUID responseId = UUID.randomUUID();
        String updatedContent = "Updated content";

        Participant mockParticipant = new Participant();
        mockParticipant.setParticipantId(UUID.randomUUID());

        ParticipantResponse mockResponse = new ParticipantResponse();
        mockResponse.setId(responseId);

        when(participantService.getParticipantForSession(any(HttpServletRequest.class), eq(retroId)))
            .thenReturn(mockParticipant);
        when(responseService.updateResponse(eq(responseId), eq(mockParticipant), eq(updatedContent)))
            .thenReturn(mockResponse);

        mockMvc.perform(put("/api/retros/{retroId}/responses/{responseId}", retroId, responseId)
                .with(SecurityMockMvcRequestPostProcessors.csrf())
                .param("content", updatedContent))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseId").value(responseId.toString()))
                .andExpect(jsonPath("$.content").value(updatedContent));
    }

    @Test
    @WithMockUser(roles = "USER")
    void revealResponses_Facilitator_ShouldReturnJsonRevealResult() throws Exception {
        UUID retroId = UUID.randomUUID();
        Long stepId = 1L;

        RetroSession mockSession = new RetroSession();
        mockSession.setId(retroId);

        when(participantService.isFacilitator(any(HttpServletRequest.class), eq(retroId)))
            .thenReturn(true);
        when(retroSessionService.getSessionById(retroId)).thenReturn(mockSession);

        mockMvc.perform(post("/api/retros/{retroId}/steps/{stepId}/responses/reveal", retroId, stepId)
                .with(SecurityMockMvcRequestPostProcessors.csrf()))
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

        mockMvc.perform(post("/api/retros/{retroId}/steps/{stepId}/responses/reveal", retroId, stepId)
                .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    void getRatingResponses_UsesStepQuerySurface() throws Exception {
        UUID retroId = UUID.randomUUID();
        Long stepId = 1L;

        RetroSession session = new RetroSession();
        RetroStage stage = new RetroStage();
        RetroStep step = new RetroStep();
        step.setId(stepId);
        step.setRetroStage(stage);

        when(participantService.isParticipating(any(HttpServletRequest.class), eq(retroId))).thenReturn(true);
        when(retroSessionService.getSessionById(retroId)).thenReturn(session);
        when(retroStepQueryService.getStepById(stepId)).thenReturn(step);
        when(responseService.getResponsesForStageComponentType(session, stage, direct.reflect.facilitator.configurator.ComponentType.RATING_SCALE))
            .thenReturn(java.util.List.of());

        mockMvc.perform(get(
                "/api/retros/{retroId}/steps/{stepId}/responses/rating", retroId, stepId))
            .andExpect(status().isOk())
            .andExpect(content().json("[]"));
    }
}
