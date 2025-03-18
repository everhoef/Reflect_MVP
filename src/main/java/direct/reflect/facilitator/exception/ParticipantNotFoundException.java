package direct.reflect.facilitator.exception;

/**
 * Thrown when a participant cannot be found.
 */
public class ParticipantNotFoundException extends RuntimeException {
    public ParticipantNotFoundException(String message) {
        super(message);
    }
}
