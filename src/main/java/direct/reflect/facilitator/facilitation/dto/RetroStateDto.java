package direct.reflect.facilitator.facilitation.dto;

import direct.reflect.facilitator.facilitation.session.dto.AssistantStateDto;
import java.util.List;
import java.util.UUID;

public record RetroStateDto(
    UUID retroId,
    long syncVersion,
    String phase,
    Long currentStepId,
    int currentStepIndex,
    List<StepSummaryDto> steps,
    UUID facilitatorId,
    boolean isFacilitator,
    int participantCount,
    AssistantStateDto assistantState
) { }
