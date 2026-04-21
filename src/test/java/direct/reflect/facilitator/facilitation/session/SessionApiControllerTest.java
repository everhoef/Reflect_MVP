package direct.reflect.facilitator.facilitation.session;

import direct.reflect.facilitator.facilitation.participant.Participant;
import direct.reflect.facilitator.facilitation.participant.ParticipantRole;
import direct.reflect.facilitator.facilitation.participant.ParticipantService;
import direct.reflect.facilitator.facilitation.session.RetroSession;
import direct.reflect.facilitator.facilitation.session.RetroSessionService;
import direct.reflect.facilitator.facilitation.session.RetroSyncVersionService;
import direct.reflect.facilitator.facilitation.session.dto.AssistantStateDto;
import direct.reflect.facilitator.facilitation.session.dto.CreateRetroRequest;
import direct.reflect.facilitator.configurator.RetroTemplate;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;

@WebMvcTest(controllers = {
    SessionApiController.class,
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
    private direct.reflect.facilitator.configurator.RetroStepRepository stepRepository;

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
    void getTimerState_ValidParticipant_ShouldReturnTimerState() throws Exception {
        UUID retroId = UUID.randomUUID();
        TimerStateDto timerState = new TimerStateDto(300, false, "green");
        
        when(retroSessionService.getTimerState(retroId)).thenReturn(timerState);
        when(retroSyncVersionService.getSyncVersion(retroId)).thenReturn(12L);

        mockMvc.perform(get("/api/retro/{retroId}/timer", retroId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.syncVersion").value(12))
                .andExpect(jsonPath("$.data.remainingSeconds").value(300))
                .andExpect(jsonPath("$.data.isPaused").value(false))
                .andExpect(jsonPath("$.data.state").value("green"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getRetroState_ValidParticipant_ShouldIncludeAssistantState() throws Exception {
        UUID retroId = UUID.randomUUID();

        RetroTemplate mockTemplate = org.mockito.Mockito.mock(RetroTemplate.class);
        when(mockTemplate.getStageForPhase(any())).thenReturn(null);

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

        mockMvc.perform(get("/api/retro/{retroId}/state", retroId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.retroId").value(retroId.toString()))
                .andExpect(jsonPath("$.syncVersion").value(21))
                .andExpect(jsonPath("$.assistantState").exists())
                .andExpect(jsonPath("$.assistantState.history").isArray());
    }

    @Test
    void shouldRequireAuthenticationForSessionCreation() throws Exception {
        mockMvc.perform(post("/api/retro/create")
                .with(anonymous()) 
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionName\":\"Test Session\"}"))
                .andExpect(status().isUnauthorized());
    }
}
