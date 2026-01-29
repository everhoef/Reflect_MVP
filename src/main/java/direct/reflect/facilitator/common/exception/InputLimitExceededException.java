package direct.reflect.facilitator.common.exception;

/**
 * Thrown when a participant attempts to submit more than the allowed number of inputs per step.
 */
public class InputLimitExceededException extends RuntimeException {
    private final long inputsSubmitted;
    private final int inputLimit;

    public InputLimitExceededException(long inputsSubmitted, int inputLimit) {
        super(String.format("Input limit exceeded. You have submitted %d of %d allowed inputs for this step.", 
            inputsSubmitted, inputLimit));
        this.inputsSubmitted = inputsSubmitted;
        this.inputLimit = inputLimit;
    }

    public long getInputsSubmitted() { return inputsSubmitted; }
    public int getInputLimit() { return inputLimit; }
}
