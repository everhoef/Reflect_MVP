package direct.reflect.facilitator.facilitation.participant.dto;

import java.util.UUID;

/**
 * DTO representing active session information for a participant.
 * Used in API responses to show what sessions a user is currently participating in.
 */
public record SessionInfo(
    UUID sessionId,
    String sessionName,
    String role
) { }
