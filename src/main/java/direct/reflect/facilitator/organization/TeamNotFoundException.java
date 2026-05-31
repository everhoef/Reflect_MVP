package direct.reflect.facilitator.organization;

public class TeamNotFoundException extends RuntimeException {
    public TeamNotFoundException() {
        super("Team not found");
    }
}
