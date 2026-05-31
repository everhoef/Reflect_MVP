package direct.reflect.facilitator.eventing;

import direct.reflect.facilitator.facilitation.participant.SseParticipantAccess;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@Slf4j
@RequiredArgsConstructor
public class RetroEventController {

    private final EventService eventService;
    private final SseParticipantAccess sseParticipantAccess;

    @GetMapping(value = "/api/retros/{retroId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
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

            SseParticipantAccess.SseParticipantConnection participant =
                    sseParticipantAccess.authorizeSseConnection(request, retroId);

            log.debug("[SSE] Participant validated - participantId: {}, name: {}, role: {}",
                participant.participantId(), participant.displayName(), "authorized");

            // Create and return SseEmitter for this participant
            SseEmitter emitter = eventService.createSseEmitter(
                retroId, participant.participantId(), participant.displayName());

            log.debug("[SSE] Connection created successfully for participant {} in retro {}",
                participant.displayName(), retroId);

            return emitter;

        } catch (Exception e) {
            log.error("[SSE] Connection failed - retroId: {}, error: {}",
                retroId, e.getMessage(), e);
            throw e;
        }
    }
}
