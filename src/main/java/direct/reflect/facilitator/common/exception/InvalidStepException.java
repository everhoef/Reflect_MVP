package direct.reflect.facilitator.common.exception;

/**
 * Exception thrown when attempting to submit a response for a step that is not the current step.
 * This prevents participants from submitting responses out of sequence.
 */
public class InvalidStepException extends RuntimeException {
  public InvalidStepException(Long requestedStepId, Long currentStepId) {
    super("Cannot submit response for step " + requestedStepId + ". Current step is " + currentStepId + ".");
  }
}
