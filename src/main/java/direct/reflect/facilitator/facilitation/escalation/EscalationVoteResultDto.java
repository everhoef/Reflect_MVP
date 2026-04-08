package direct.reflect.facilitator.facilitation.escalation;

import java.util.UUID;

public record EscalationVoteResultDto(
        UUID escalationId,
        long voteCount,
        int threshold,
        boolean thresholdMet,
        boolean voted) {
}
