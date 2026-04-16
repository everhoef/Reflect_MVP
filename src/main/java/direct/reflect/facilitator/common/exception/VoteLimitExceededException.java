package direct.reflect.facilitator.common.exception;

import lombok.Getter;

/**
 * Thrown when a participant attempts to vote but has already used all available votes.
 */
@Getter
public class VoteLimitExceededException extends RuntimeException {

    private final long votesUsed;
    private final int voteLimit;

    public VoteLimitExceededException(long votesUsed, int voteLimit) {
        super(String.format("Vote limit exceeded. You have used %d of %d votes.", votesUsed, voteLimit));
        this.votesUsed = votesUsed;
        this.voteLimit = voteLimit;
    }
}
