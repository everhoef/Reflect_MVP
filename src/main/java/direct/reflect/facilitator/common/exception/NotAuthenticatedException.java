package direct.reflect.facilitator.common.exception;

/**
 * Thrown when an operation requires authentication but the user is not authenticated.
 */
public class NotAuthenticatedException extends RuntimeException {
    public NotAuthenticatedException(String message) {
        super(message);
    }
}
