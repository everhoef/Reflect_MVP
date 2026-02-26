package direct.reflect.facilitator.facilitation.actionitem;

import direct.reflect.facilitator.common.entity.GeneratedUuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

// Participant fields store plain UUIDs (no JPA relation) because Participant uses a composite PK.
@Entity
@Table(name = "action_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActionItem {

    @Id
    @GeneratedUuidV7
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false)
    private String what;

    @Column(name = "assigned_to_participant_id")
    private UUID assignedToParticipantId;

    @Column(name = "when_date")
    private LocalDate whenDate;

    @Column(name = "success_criteria")
    private String successCriteria;

    @Column(name = "retro_session_id", nullable = false)
    private UUID retroSessionId;

    @Column(name = "retro_step_id")
    private Long retroStepId;

    @Column(name = "created_by_participant_id")
    private UUID createdByParticipantId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
