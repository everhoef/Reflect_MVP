package direct.reflect.facilitator.facilitation.escalation;

import java.util.UUID;

public record EscalationVoteResultDto(
        long syncVersion,
        UUID escalationId,
        long voteCount,
        int threshold,
        boolean thresholdMet,
        boolean voted) {

    public EscalationVoteResultDto withSyncVersion(long syncVersion) {
        return new EscalationVoteResultDto(syncVersion, escalationId, voteCount, threshold, thresholdMet, voted);
    }
}
