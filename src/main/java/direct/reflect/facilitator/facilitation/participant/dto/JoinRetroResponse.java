package direct.reflect.facilitator.facilitation.participant.dto;

import java.util.UUID;

public record JoinRetroResponse(
    UUID retroId,
    String redirectUrl
) {}
