package direct.reflect.facilitator.organization;

public class OrganizationNotFoundException extends RuntimeException {
    public OrganizationNotFoundException() {
        super("Organization not found");
    }
}
