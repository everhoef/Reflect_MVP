package direct.reflect.facilitator.facilitation.dto;

import java.util.UUID;

public record AssistantMessageDto(
    UUID retroId,
    Long stepId,
    String stepTitle,
    String publicText
) {}
