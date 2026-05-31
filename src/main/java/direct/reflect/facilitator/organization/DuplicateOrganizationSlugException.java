package direct.reflect.facilitator.organization;

public class DuplicateOrganizationSlugException extends RuntimeException {

    public DuplicateOrganizationSlugException(String slug) {
        super("Organization slug already exists: " + slug);
    }
}
