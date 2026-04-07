package direct.reflect.facilitator.organization;

import java.util.UUID;

public record TeamDto(
        UUID id,
        UUID organizationId,
        String name
) {
    public static TeamDto from(Team team) {
        return new TeamDto(
                team.getId(),
                team.getOrganization().getId(),
                team.getName()
        );
    }
}
