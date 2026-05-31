package direct.reflect.facilitator.facilitation.escalation.domain;

public final class EscalationThresholdPolicy {

    private EscalationThresholdPolicy() {
    }

    public static int calculateVoteThreshold(int participantCount) {
        if (participantCount < 1) {
            throw new IllegalArgumentException("Participant count must be at least 1");
        }

        return (participantCount / 2) + 1;
    }

    public static boolean hasReachedThreshold(long voteCount, int threshold) {
        return voteCount >= threshold;
    }

    public static boolean isTieBreakScenario(long voteCount, int threshold) {
        return voteCount == threshold - 1;
    }

    public static boolean facilitatorTieBreakApplies(int participantCount, boolean facilitatorVoted) {
        return participantCount % 2 == 0 && facilitatorVoted;
    }
}
