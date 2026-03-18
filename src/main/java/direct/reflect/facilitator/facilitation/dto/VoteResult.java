package direct.reflect.facilitator.facilitation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Result of toggling a vote on a response")
public record VoteResult(
    @Schema(description = "ID of the response that was voted on")
    UUID responseId,

    @Schema(description = "Updated total vote count for this response")
    int voteCount
) {}
