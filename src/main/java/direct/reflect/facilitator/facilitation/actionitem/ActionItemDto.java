package direct.reflect.facilitator.facilitation.actionitem;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActionItemDto {

    private UUID id;
    private String what;
    private UUID assignedToParticipantId;
    private String assignedToDisplayName;
    private LocalDate whenDate;
    private String successCriteria;
    private UUID retroSessionId;
    private Long retroStepId;
    private UUID createdByParticipantId;
    private Instant createdAt;
}
