package direct.reflect.facilitator.facilitation.participant;

import direct.reflect.facilitator.facilitation.participant.dto.JoinRetroRequest;
import direct.reflect.facilitator.facilitation.session.RetroSession;
import direct.reflect.facilitator.facilitation.session.RetroSessionService;
import direct.reflect.facilitator.facilitation.session.RetroSyncVersionService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {
    ParticipantApiController.class,
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
public class ParticipantApiControllerTest {

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
    private RetroSyncVersionService retroSyncVersionService;

    @MockitoBean
    private direct.reflect.facilitator.eventing.EventService eventService;

    @Test
    @WithMockUser(roles = "GUEST")
    void shouldJoinRetrospectiveAndReturnJson() throws Exception {
        UUID retroId = UUID.randomUUID();
        RetroSession mockSession = new RetroSession();
        mockSession.setId(retroId);
        mockSession.setName("Existing Session");

        Participant mockParticipant = new Participant();
        mockParticipant.setParticipantId(UUID.randomUUID()); 
        mockParticipant.setDisplayName("Guest");
        mockParticipant.setSession(mockSession);

        when(retroSessionService.getSessionById(retroId)).thenReturn(mockSession); 
        when(participantService.addParticipantToSession(any(HttpServletRequest.class), eq(mockSession), eq(ParticipantRole.PARTICIPANT)))
            .thenReturn(mockParticipant);

        mockMvc.perform(post("/api/retros/{retroId}/participants", retroId)
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.retroId").value(retroId.toString()))
                .andExpect(jsonPath("$.redirectUrl").value("/retro/" + retroId));
    }

    @Test
    @WithMockUser(roles = "USER")
    void leaveActiveSessions_ShouldReturnJsonWithSuccessTrue() throws Exception {
        mockMvc.perform(delete("/api/me/retros/active")
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getParticipants_ValidParticipant_ShouldReturnSyncVersionedParticipants() throws Exception {
        UUID retroId = UUID.randomUUID();
        Participant facilitator = new Participant();
        facilitator.setParticipantId(UUID.randomUUID());
        facilitator.setDisplayName("Facilitator");
        facilitator.setRole(ParticipantRole.FACILITATOR);

        Participant participant = new Participant();
        participant.setParticipantId(UUID.randomUUID());
        participant.setDisplayName("Participant");
        participant.setRole(ParticipantRole.PARTICIPANT);

        when(participantService.getParticipantForSession(any(HttpServletRequest.class), eq(retroId)))
            .thenReturn(facilitator);
        when(participantService.getSessionParticipants(retroId)).thenReturn(List.of(facilitator, participant));
        when(retroSyncVersionService.getSyncVersion(retroId)).thenReturn(34L);

        mockMvc.perform(get("/api/retros/{retroId}/participants", retroId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.syncVersion").value(34))
                .andExpect(jsonPath("$.data[0].displayName").value("Facilitator"))
                .andExpect(jsonPath("$.data[0].role").value("FACILITATOR"))
                .andExpect(jsonPath("$.data[1].displayName").value("Participant"))
                .andExpect(jsonPath("$.data[1].role").value("PARTICIPANT"));
    }
}
