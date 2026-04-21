package direct.reflect.facilitator.facilitation.response;

import lombok.Getter;

@Getter
public class InputLimitExceededException extends RuntimeException {
    private final long inputsSubmitted;
    private final int inputLimit;

    public InputLimitExceededException(long inputsSubmitted, int inputLimit) {
        super(String.format("Input limit exceeded. You have submitted %d of %d allowed inputs for this step.",
            inputsSubmitted, inputLimit));
        this.inputsSubmitted = inputsSubmitted;
        this.inputLimit = inputLimit;
    }
}
