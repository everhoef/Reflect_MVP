package direct.reflect.facilitator.organization;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTeamRequest(
        @NotBlank(message = "Team name is required")
        @Size(max = 100, message = "Team name must be at most 100 characters")
        String name
) {}
