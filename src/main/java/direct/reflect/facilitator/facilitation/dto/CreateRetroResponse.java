package direct.reflect.facilitator.facilitation.dto;

import java.util.UUID;

public record CreateRetroResponse(
    UUID retroId,
    String redirectUrl,
    String sessionName
) {}
