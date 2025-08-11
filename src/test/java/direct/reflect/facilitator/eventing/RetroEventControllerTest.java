package direct.reflect.facilitator.eventing;

import direct.reflect.facilitator.facilitation.ParticipantService;
import direct.reflect.facilitator.facilitation.Participant;
import direct.reflect.facilitator.common.exception.ParticipantNotFoundException;
import direct.reflect.facilitator.common.exception.ApiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import jakarta.servlet.http.HttpServletRequest;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = RetroEventController.class)
@Import({ApiExceptionHandler.class, direct.reflect.facilitator.common.config.SecurityConfig.class})
@EnableAutoConfiguration(exclude = {
    DataSourceAutoConfiguration.class,
    HibernateJpaAutoConfiguration.class
})
class RetroEventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EventService eventService;

    @MockitoBean
    private ParticipantService participantService;

    @Test
    @WithMockUser(roles = "USER")
    void shouldReturnSseEmitterWhenUserIsAuthorized() throws Exception {
        // Arrange
        UUID retroId = UUID.randomUUID();
        Participant participant = new Participant();
        participant.setParticipantId(UUID.randomUUID());
        participant.setDisplayName("Test User");
        
        when(participantService.getParticipantForSession(any(HttpServletRequest.class), eq(retroId)))
            .thenReturn(participant);
        when(eventService.createSseEmitter(eq(retroId), any(HttpServletRequest.class)))
            .thenReturn(new SseEmitter());

        // Act & Assert
        mockMvc.perform(get("/api/retro/{retroId}/events", retroId)
                .accept(MediaType.TEXT_EVENT_STREAM_VALUE))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.TEXT_EVENT_STREAM_VALUE));
    }

    @Test
    @WithMockUser(roles = "GUEST") 
    void shouldReturnSseEmitterWhenGuestIsAuthorized() throws Exception {
        // Arrange
        UUID retroId = UUID.randomUUID();
        Participant participant = new Participant();
        participant.setParticipantId(UUID.randomUUID());
        participant.setDisplayName("Test Guest");
        
        when(participantService.getParticipantForSession(any(HttpServletRequest.class), eq(retroId)))
            .thenReturn(participant);
        when(eventService.createSseEmitter(eq(retroId), any(HttpServletRequest.class)))
            .thenReturn(new SseEmitter());

        // Act & Assert
        mockMvc.perform(get("/api/retro/{retroId}/events", retroId)
                .accept(MediaType.TEXT_EVENT_STREAM_VALUE))
                .andExpect(status().isOk());
    }

    @Test
    void shouldReturn403WhenNotAuthenticated() throws Exception {
        // Arrange
        UUID retroId = UUID.randomUUID();

        // Act & Assert
        mockMvc.perform(get("/api/retro/{retroId}/events", retroId)
                .accept(MediaType.TEXT_EVENT_STREAM_VALUE))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    void shouldReturn404WhenParticipantNotFound() throws Exception {
        // Arrange
        UUID retroId = UUID.randomUUID();
        
        when(participantService.getParticipantForSession(any(HttpServletRequest.class), eq(retroId)))
            .thenThrow(new ParticipantNotFoundException("Not authorized for session"));

        // Act & Assert
        mockMvc.perform(get("/api/retro/{retroId}/events", retroId)
                .accept(MediaType.TEXT_EVENT_STREAM_VALUE))
                .andExpect(status().isNotFound());
    }
}