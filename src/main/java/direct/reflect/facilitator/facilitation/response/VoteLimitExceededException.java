package direct.reflect.facilitator.facilitation.response;

import lombok.Getter;

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
