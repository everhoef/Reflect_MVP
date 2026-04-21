package direct.reflect.facilitator.facilitation.session.dto;

import java.util.UUID;

public record NextStepResult(
    UUID retroId,
    boolean advanced
) {}
