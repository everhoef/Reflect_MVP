package direct.reflect.facilitator.facilitation.dto;

import java.util.List;
import java.util.UUID;

public record RetroStateDto(
    UUID retroId,
    String phase,
    Long currentStepId,
    int currentStepIndex,
    List<StepSummaryDto> steps,
    UUID facilitatorId,
    boolean isFacilitator,
    int participantCount
) {}
