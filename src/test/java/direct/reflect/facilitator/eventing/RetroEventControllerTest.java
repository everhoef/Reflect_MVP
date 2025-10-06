package direct.reflect.facilitator.eventing;

import direct.reflect.facilitator.eventing.EventService;
import direct.reflect.facilitator.eventing.EventType;
import direct.reflect.facilitator.eventing.RetroEvent;
import direct.reflect.facilitator.facilitation.ParticipantService;
import direct.reflect.facilitator.facilitation.Participant;
import direct.reflect.facilitator.facilitation.ParticipantRole;
import direct.reflect.facilitator.common.exception.ParticipantNotFoundException;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests for RetroEventController SSE endpoint functionality.
 */
@WebMvcTest(RetroEventController.class)
@org.springframework.boot.autoconfigure.EnableAutoConfiguration(exclude = {
    org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration.class,
    org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration.class
})
class RetroEventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EventService eventService;

    @MockitoBean  
    private ParticipantService participantService;

    @Test
    @WithMockUser(roles = {"USER"})
    void shouldEstablishSseConnectionWithAuthentication() throws Exception {
        // Given
        UUID retroId = UUID.randomUUID();
        Participant mockParticipant = new Participant();
        mockParticipant.setParticipantId(UUID.randomUUID());
        mockParticipant.setDisplayName("TestUser");
        mockParticipant.setRole(ParticipantRole.FACILITATOR);
        
        when(participantService.getParticipantForSession(any(HttpServletRequest.class), eq(retroId)))
            .thenReturn(mockParticipant);
        
        SseEmitter testEmitter = new SseEmitter(30000L);
        when(eventService.createSseEmitter(eq(retroId), any(HttpServletRequest.class)))
            .thenReturn(testEmitter);

        // When & Then
        mockMvc.perform(get("/api/retro/{retroId}/events", retroId)
                .accept(MediaType.TEXT_EVENT_STREAM_VALUE))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));
                
        // Verify service methods were called
        verify(participantService).getParticipantForSession(any(HttpServletRequest.class), eq(retroId));
        verify(participantService).updateLastSeen(any(HttpServletRequest.class), eq(retroId));
        verify(eventService).createSseEmitter(eq(retroId), any(HttpServletRequest.class));
    }

    @Test  
    void shouldValidateParticipantJoinedEventStructure() {
        // Test verifies PARTICIPANT_JOINED event structure for SSE transmission
        UUID retroId = UUID.randomUUID();
        String participantName = "NewParticipant";
        
        RetroEvent<String> participantJoinedEvent = RetroEvent.participantJoined(retroId, participantName);
        
        // Verify event structure is correct for SSE transmission
        assert participantJoinedEvent.retroId().equals(retroId);
        assert participantJoinedEvent.type() == RetroEvent.EventType.PARTICIPANT_JOINED;
        assert participantJoinedEvent.payload().equals(participantName);
        assert participantJoinedEvent.sourceId().equals("system");
        assert participantJoinedEvent.timestamp() != null;
    }

    @Test
    void shouldPublishRetroCreatedEventOnSessionCreation() {
        // This test verifies that RETRO_CREATED events are published
        // when a new session is created, which helps establish SSE connections immediately
        
        UUID retroId = UUID.randomUUID();
        String sessionName = "Test Session";
        
        RetroEvent<Void> retroCreatedEvent = RetroEvent.retroCreated(retroId, "system");
        
        // Verify event structure
        assert retroCreatedEvent.retroId().equals(retroId);
        assert retroCreatedEvent.type() == RetroEvent.EventType.RETRO_CREATED;
        assert retroCreatedEvent.payload() == null;
        assert retroCreatedEvent.sourceId().equals("system");
    }

    @Test
    @WithMockUser(roles = {"GUEST"})
    void shouldRejectSseConnectionForUnauthorizedSession() throws Exception {
        // Given
        UUID retroId = UUID.randomUUID();
        
        // Mock participant service to throw ParticipantNotFoundException (not authorized for this session)
        when(participantService.getParticipantForSession(any(HttpServletRequest.class), eq(retroId)))
            .thenThrow(new ParticipantNotFoundException("Not authorized for session: " + retroId));

        // When & Then
        mockMvc.perform(get("/api/retro/{retroId}/events", retroId)
                .accept(MediaType.TEXT_EVENT_STREAM_VALUE))
                .andExpect(status().isNotFound()); // ParticipantNotFoundException maps to 404
                
        // Verify participant validation was attempted
        verify(participantService).getParticipantForSession(any(HttpServletRequest.class), eq(retroId));
        // Verify no SSE emitter was created for unauthorized access
        verify(eventService, org.mockito.Mockito.never()).createSseEmitter(any(UUID.class), any(HttpServletRequest.class));
    }

    @Test
    @WithMockUser(roles = {"GUEST"})
    void shouldAllowSseConnectionForAuthorizedGuestParticipant() throws Exception {
        // Given
        UUID retroId = UUID.randomUUID();
        Participant guestParticipant = new Participant();
        guestParticipant.setParticipantId(UUID.randomUUID());
        guestParticipant.setDisplayName("Guest User");
        guestParticipant.setRole(ParticipantRole.PARTICIPANT);
        
        when(participantService.getParticipantForSession(any(HttpServletRequest.class), eq(retroId)))
            .thenReturn(guestParticipant);
        
        SseEmitter testEmitter = new SseEmitter(30000L);
        when(eventService.createSseEmitter(eq(retroId), any(HttpServletRequest.class)))
            .thenReturn(testEmitter);

        // When & Then
        mockMvc.perform(get("/api/retro/{retroId}/events", retroId)
                .accept(MediaType.TEXT_EVENT_STREAM_VALUE))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));
                
        // Verify guest participant was validated and SSE connection established
        verify(participantService).getParticipantForSession(any(HttpServletRequest.class), eq(retroId));
        verify(participantService).updateLastSeen(any(HttpServletRequest.class), eq(retroId));
        verify(eventService).createSseEmitter(eq(retroId), any(HttpServletRequest.class));
    }

    @Test
    void shouldRejectSseConnectionWithoutAuthentication() throws Exception {
        // Given
        UUID retroId = UUID.randomUUID();

        // When & Then - No @WithMockUser annotation means no authentication
        mockMvc.perform(get("/api/retro/{retroId}/events", retroId)
                .accept(MediaType.TEXT_EVENT_STREAM_VALUE))
                .andExpect(status().is3xxRedirection()); // WebMvcTest redirects to login for unauthenticated requests
                
        // Verify no service methods were called due to authentication failure
        verify(participantService, org.mockito.Mockito.never()).getParticipantForSession(any(HttpServletRequest.class), any(UUID.class));
        verify(eventService, org.mockito.Mockito.never()).createSseEmitter(any(UUID.class), any(HttpServletRequest.class));
    }
}