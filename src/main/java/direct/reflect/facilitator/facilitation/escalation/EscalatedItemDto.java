package direct.reflect.facilitator.facilitation.escalation;

import java.time.LocalDateTime;
import java.util.UUID;

public record EscalatedItemDto(
        UUID id,
        String problemDescription,
        long voteCount,
        int threshold,
        boolean thresholdMet,
        LocalDateTime createdAt) {

    public static EscalatedItemDto from(EscalatedItem escalatedItem, long voteCount, boolean thresholdMet) {
        return new EscalatedItemDto(
                escalatedItem.getId(),
                escalatedItem.getProblemDescription(),
                voteCount,
                escalatedItem.getVoteThreshold(),
                thresholdMet,
                escalatedItem.getCreatedAt());
    }
}
