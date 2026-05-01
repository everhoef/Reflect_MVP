package direct.reflect.facilitator.facilitation.dto;

import java.util.Map;

public record StepSummaryDto(
    Long id,
    String title,
    String componentType,
    String advancementTrigger,
    Integer durationSeconds,
    Map<String, Object> componentConfig,
    String guidance
) { }
