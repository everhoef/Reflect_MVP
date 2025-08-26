package direct.reflect.facilitator.facilitation.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request DTO for joining an existing retrospective session.
 * Uses Bean Validation for input validation.
 */
public record JoinRetroRequest(
    @NotNull(message = "Retro ID is required")
    UUID retroId
) {}