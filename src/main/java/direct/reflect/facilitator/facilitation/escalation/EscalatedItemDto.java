package direct.reflect.facilitator.facilitation.escalation;

import java.time.LocalDateTime;
import java.util.UUID;

public record EscalatedItemDto(
        long syncVersion,
        UUID id,
        String problemDescription,
        long voteCount,
        int threshold,
        boolean thresholdMet,
        LocalDateTime createdAt) {

    public static EscalatedItemDto from(EscalatedItem escalatedItem, long voteCount, boolean thresholdMet) {
        return new EscalatedItemDto(
                0L,
                escalatedItem.getId(),
                escalatedItem.getProblemDescription(),
                voteCount,
                escalatedItem.getVoteThreshold(),
                thresholdMet,
                escalatedItem.getCreatedAt());
    }

    public EscalatedItemDto withSyncVersion(long syncVersion) {
        return new EscalatedItemDto(
                syncVersion,
                id,
                problemDescription,
                voteCount,
                threshold,
                thresholdMet,
                createdAt);
    }
}
