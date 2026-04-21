package direct.reflect.facilitator.facilitation.actions;

import direct.reflect.facilitator.common.ids.GeneratedUuidV7;
import direct.reflect.facilitator.facilitation.session.RetroSession;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Data;

@Entity
@Table(
        name = "action_items",
        indexes = {
                @Index(name = "idx_action_items_retro_session", columnList = "retro_session_id"),
                @Index(name = "idx_action_items_retro_session_status", columnList = "retro_session_id,status")
        })
@Data
public class ActionItem {

    @Id
    @GeneratedUuidV7
    @Column(name = "id", nullable = false, unique = true, updatable = false)
    private UUID id;

    @NotBlank(message = "Action item 'what' is required")
    @Size(max = 500, message = "Action item 'what' must not exceed 500 characters")
    @Column(name = "what", nullable = false, length = 500)
    private String what;

    @NotBlank(message = "Action item 'who' is required")
    @Size(max = 200, message = "Action item 'who' must not exceed 200 characters")
    @Column(name = "who", nullable = false, length = 200)
    private String who;

    @NotNull(message = "Action item due date is required")
    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Size(max = 500, message = "Success criteria must not exceed 500 characters")
    @Column(name = "success_criteria", length = 500)
    private String successCriteria;

    @Column(name = "escalated", nullable = false)
    private Boolean escalated = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ActionItemStatus status = ActionItemStatus.OPEN;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "retro_session_id", nullable = false)
    private RetroSession retroSession;

    @Column(name = "created_by_participant_id")
    private UUID createdByParticipantId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        if (escalated == null) {
            escalated = false;
        }
        if (status == null) {
            status = ActionItemStatus.OPEN;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
