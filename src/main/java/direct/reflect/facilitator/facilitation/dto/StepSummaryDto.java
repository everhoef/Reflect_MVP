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
) {
    public StepSummaryDto {
        componentConfig = componentConfig != null ? Map.copyOf(componentConfig) : null;
    }

    @Override
    public Map<String, Object> componentConfig() {
        return componentConfig != null ? Map.copyOf(componentConfig) : null;
    }
}
