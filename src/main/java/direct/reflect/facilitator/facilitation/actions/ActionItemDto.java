package direct.reflect.facilitator.facilitation.actions;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record ActionItemDto(
        long syncVersion,
        UUID id,
        String what,
        String who,
        LocalDate dueDate,
        String successCriteria,
        boolean escalated,
        ActionItemStatus status,
        UUID createdByParticipantId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static ActionItemDto from(ActionItem actionItem) {
        return new ActionItemDto(
                0L,
                actionItem.getId(),
                actionItem.getWhat(),
                actionItem.getWho(),
                actionItem.getDueDate(),
                actionItem.getSuccessCriteria(),
                Boolean.TRUE.equals(actionItem.getEscalated()),
                actionItem.getStatus(),
                actionItem.getCreatedByParticipantId(),
                actionItem.getCreatedAt(),
                actionItem.getUpdatedAt());
    }

    public ActionItemDto withSyncVersion(long syncVersion) {
        return new ActionItemDto(
                syncVersion,
                id,
                what,
                who,
                dueDate,
                successCriteria,
                escalated,
                status,
                createdByParticipantId,
                createdAt,
                updatedAt);
    }
}
