package direct.reflect.facilitator.facilitation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for creating a new retrospective session.
 * Uses Bean Validation for input validation.
 */
public record CreateRetroRequest(
    @NotBlank(message = "Session name is required")
    @Size(min = 3, max = 100, message = "Session name must be between 3 and 100 characters")
    String sessionName
) {}