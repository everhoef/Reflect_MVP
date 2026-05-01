package direct.reflect.facilitator.facilitation.participant.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Request DTO for joining an existing retrospective session.
 * Uses Bean Validation for input validation.
 */
public record JoinRetroRequest(
    @NotNull(message = "Retro ID is required")
    @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
             message = "Retro ID must be a valid UUID")
    String retroId
) { }
