package direct.reflect.facilitator.facilitation.escalation;

import direct.reflect.facilitator.common.ids.GeneratedUuidV7;
import direct.reflect.facilitator.facilitation.session.RetroSession;
import direct.reflect.facilitator.organization.Team;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.Data;
import java.util.UUID;

@Entity
@Table(
        name = "escalated_items",
        indexes = {
                @Index(name = "idx_escalated_items_retro_session", columnList = "retro_session_id"),
                @Index(name = "idx_escalated_items_team", columnList = "team_id")
        })
@Data
public class EscalatedItem {

    @Id
    @GeneratedUuidV7
    @Column(name = "id", nullable = false, unique = true, updatable = false)
    private UUID id;

    @NotBlank(message = "Escalated problem description is required")
    @Size(max = 1000, message = "Escalated problem description must not exceed 1000 characters")
    @Column(name = "problem_description", nullable = false, length = 1000)
    private String problemDescription;

    @NotNull(message = "Escalated item must belong to a retro session")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "retro_session_id", nullable = false)
    private RetroSession retroSession;

    @NotNull(message = "Escalated item must belong to a team")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @Min(value = 1, message = "Vote threshold must be at least 1")
    @Column(name = "vote_threshold", nullable = false)
    private int voteThreshold;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
