package direct.reflect.facilitator.facilitation.response;

import direct.reflect.facilitator.facilitation.participant.Participant;
import direct.reflect.facilitator.facilitation.participant.ParticipantService;
import direct.reflect.facilitator.facilitation.session.RetroSessionService;
import direct.reflect.facilitator.facilitation.session.RetroSession;
import direct.reflect.facilitator.auth.AuthService;
import direct.reflect.facilitator.configurator.RetroStep;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import jakarta.servlet.http.HttpServletRequest;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {
    ResponseApiController.class,
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
    private direct.reflect.facilitator.configurator.RetroStepRepository stepRepository;

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

        mockMvc.perform(post("/api/retro/{retroId}/step/{stepId}/response/column", retroId, stepId)
                .with(csrf())
                .param("columnId", "Mad")
                .param("content", "Too many meetings"))
                .andExpect(status().isOk())
                .andExpect(header().string("HX-Trigger", "responseSubmitted"));
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

        mockMvc.perform(put("/api/retro/{retroId}/response/{responseId}", retroId, responseId)
                .with(csrf())
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

        mockMvc.perform(post("/api/retro/{retroId}/step/{stepId}/reveal", retroId, stepId)
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(header().string("HX-Trigger", "responsesRevealed"))
                .andExpect(jsonPath("$.revealed").value(true));
    }
}
