package direct.reflect.facilitator.facilitation.escalation;

import java.util.UUID;

public class EscalatedItemNotFoundException extends RuntimeException {
    public EscalatedItemNotFoundException(UUID escalationId) {
        super("Escalated item not found: " + escalationId);
    }
}
