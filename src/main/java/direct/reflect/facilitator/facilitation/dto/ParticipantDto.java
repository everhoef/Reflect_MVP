package direct.reflect.facilitator.facilitation.dto;

import java.util.UUID;

public record ParticipantDto(
    UUID participantId,
    String displayName,
    String role
) {}
