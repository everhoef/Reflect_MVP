package direct.reflect.facilitator.organization;

import java.util.UUID;

public record OrganizationDto(
        UUID id,
        String name,
        String slug
) {
    public static OrganizationDto from(Organization organization) {
        return new OrganizationDto(
                organization.getId(),
                organization.getName(),
                organization.getSlug()
        );
    }
}
