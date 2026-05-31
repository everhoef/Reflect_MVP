package direct.reflect.facilitator.facilitation.actions;

import java.util.UUID;

public class ActionItemNotFoundException extends RuntimeException {
    public ActionItemNotFoundException(UUID actionId) {
        super("Action item not found: " + actionId);
    }
}
