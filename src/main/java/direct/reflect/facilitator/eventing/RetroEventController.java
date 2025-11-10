package direct.reflect.facilitator.eventing;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

import direct.reflect.facilitator.eventing.RetroEvent;
import direct.reflect.facilitator.eventing.EventService;
import direct.reflect.facilitator.facilitation.ParticipantService;
import direct.reflect.facilitator.facilitation.Participant;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/api/retro")
@RequiredArgsConstructor
@Slf4j
public class RetroEventController {
    private final EventService eventService;
    private final ParticipantService participantService;
    
    @GetMapping(value = "/{retroId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAnyRole('USER', 'GUEST')")
    public SseEmitter getRetroEvents(
            @PathVariable UUID retroId,
            HttpServletRequest request,
            HttpServletResponse response) {

        // Set proper SSE headers
        response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");

        try {
            // Validate participant access using session attributes (will throw exception if not authorized)
            Participant participant = participantService.getParticipantForSession(request, retroId);

            String httpSessionId = request.getSession(false) != null ? request.getSession(false).getId() : "none";
            log.debug("SSE connection established - participantId: {}, role: {}, retroId: {}, httpSessionId: {}",
                participant.getParticipantId(), participant.getRole(), retroId, httpSessionId);

            // Update last seen
            participantService.updateLastSeen(request, retroId);

            // Create and return SseEmitter for this participant
            return eventService.createSseEmitter(retroId, request, participant.getDisplayName(), participant.getParticipantId());

        } catch (Exception e) {
            String httpSessionId = request.getSession(false) != null ? request.getSession(false).getId() : "none";
            log.error("SSE connection failed - retroId: {}, httpSessionId: {}, error: {}",
                retroId, httpSessionId, e.getMessage());
            throw e;
        }
    }
}