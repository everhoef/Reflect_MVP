package direct.reflect.facilitator.eventing;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

import direct.reflect.facilitator.facilitation.participant.ParticipantService;
import direct.reflect.facilitator.facilitation.participant.Participant;

import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/retro")
@Slf4j
@RequiredArgsConstructor
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
            log.debug("[SSE] Connection request - retroId: {}, httpSessionId: {}",
                retroId, request.getSession(false) != null ? request.getSession(false).getId() : "none");

            // Validate participant access using session attributes (will throw exception if not authorized)
            Participant participant = participantService.getParticipantForSession(request, retroId);

            log.debug("[SSE] Participant validated - participantId: {}, name: {}, role: {}",
                participant.getParticipantId(), participant.getDisplayName(), participant.getRole());

            // Update last seen
            participantService.updateLastSeen(request, retroId);

            // Create and return SseEmitter for this participant
            SseEmitter emitter = eventService.createSseEmitter(
                retroId, participant.getParticipantId(), participant.getDisplayName());

            log.debug("[SSE] Connection created successfully for participant {} in retro {}",
                participant.getDisplayName(), retroId);

            return emitter;

        } catch (Exception e) {
            log.error("[SSE] Connection failed - retroId: {}, error: {}",
                retroId, e.getMessage(), e);
            throw e;
        }
    }
}
