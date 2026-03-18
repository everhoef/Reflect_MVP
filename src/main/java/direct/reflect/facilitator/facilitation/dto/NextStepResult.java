package direct.reflect.facilitator.facilitation.dto;

import java.util.UUID;

public record NextStepResult(
    UUID retroId,
    boolean advanced
) {}
