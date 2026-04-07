package direct.reflect.facilitator.organization;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AddMemberRequest(
        @NotNull(message = "User id is required")
        UUID userId,

        @NotNull(message = "Role is required")
        TeamRole role
) {}
