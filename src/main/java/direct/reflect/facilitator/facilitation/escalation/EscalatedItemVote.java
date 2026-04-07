package direct.reflect.facilitator.facilitation.escalation;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Data;

@Entity
@Table(
        name = "escalated_item_votes",
        indexes = {
                @Index(name = "idx_escalated_item_votes_escalated_item", columnList = "escalated_item_id")
        })
@Data
public class EscalatedItemVote {

    @EmbeddedId
    private EscalatedItemVoteId id;

    @MapsId("escalatedItemId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "escalated_item_id", nullable = false)
    private EscalatedItem escalatedItem;

    @Column(name = "voted_at", nullable = false)
    private LocalDateTime votedAt;

    @PrePersist
    void onCreate() {
        if (votedAt == null) {
            votedAt = LocalDateTime.now();
        }
    }

    public UUID getEscalatedItemId() {
        return id != null ? id.getEscalatedItemId() : null;
    }

    public UUID getParticipantId() {
        return id != null ? id.getParticipantId() : null;
    }

    public EscalatedItemVoteId getId() {
        return id;
    }

    public void setId(EscalatedItemVoteId id) {
        this.id = id;
    }

    public EscalatedItem getEscalatedItem() {
        return escalatedItem;
    }

    public void setEscalatedItem(EscalatedItem escalatedItem) {
        this.escalatedItem = escalatedItem;
    }

    public LocalDateTime getVotedAt() {
        return votedAt;
    }

    public void setVotedAt(LocalDateTime votedAt) {
        this.votedAt = votedAt;
    }
}
