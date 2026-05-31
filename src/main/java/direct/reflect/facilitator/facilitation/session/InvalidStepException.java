package direct.reflect.facilitator.facilitation.session;

public class InvalidStepException extends RuntimeException {
    public InvalidStepException(Long requestedStepId, Long currentStepId) {
        super("Cannot submit response for step " + requestedStepId + ". Current step is " + currentStepId + ".");
    }
}
