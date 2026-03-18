package direct.reflect.facilitator.facilitation.dto;

import java.util.UUID;

public record JoinRetroResponse(
    UUID retroId,
    String redirectUrl
) {}
