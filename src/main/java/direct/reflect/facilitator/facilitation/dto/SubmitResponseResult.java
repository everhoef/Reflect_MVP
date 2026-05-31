package direct.reflect.facilitator.facilitation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Result of submitting a participant response")
public record SubmitResponseResult(
    @Schema(description = "ID of the created/updated response", example = "550e8400-e29b-41d4-a716-446655440000")
    UUID responseId,

    @Schema(description = "ID of the step the response belongs to", example = "1")
    Long stepId
) { }
