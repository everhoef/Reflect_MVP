package direct.reflect.facilitator.facilitation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Result of updating a participant response")
public record UpdateResponseResult(
    @Schema(description = "ID of the updated response")
    UUID responseId,

    @Schema(description = "Updated content of the response")
    String content
) { }
