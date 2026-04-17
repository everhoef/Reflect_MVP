package direct.reflect.facilitator.facilitation.actions;

import jakarta.validation.constraints.NotNull;

public record UpdateActionItemStatusRequest(
        @NotNull(message = "Action item status is required")
        ActionItemStatus status) {
}
