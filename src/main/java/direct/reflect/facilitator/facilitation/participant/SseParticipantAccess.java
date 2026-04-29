package direct.reflect.facilitator.facilitation.participant;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;

public interface SseParticipantAccess {

    /**
     * Validates SSE access and returns the emitter identity payload.
     */
    SseParticipantConnection authorizeSseConnection(
            HttpServletRequest request,
            UUID sessionId);

    record SseParticipantConnection(UUID participantId, String displayName) {
    }
}
