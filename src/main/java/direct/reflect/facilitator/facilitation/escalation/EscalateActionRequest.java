package direct.reflect.facilitator.facilitation.escalation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EscalateActionRequest(
        @NotBlank(message = "Escalated problem description is required")
        @Size(max = 1000, message = "Escalated problem description must not exceed 1000 characters")
        String problemDescription) {
}
