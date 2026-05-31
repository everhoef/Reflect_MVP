package direct.reflect.facilitator.facilitation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Result of revealing responses for a step")
public record RevealResult(
    @Schema(description = "ID of the step whose responses were revealed")
    Long stepId,

    @Schema(description = "Always true — indicates reveal was successful")
    boolean revealed
) { }
