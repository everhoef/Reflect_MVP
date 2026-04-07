package direct.reflect.facilitator.facilitation.escalation;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class EscalatedItemVoteId implements Serializable {
    private static final long serialVersionUID = 1L;

    @Column(name = "escalated_item_id", nullable = false)
    private UUID escalatedItemId;

    @Column(name = "participant_id", nullable = false)
    private UUID participantId;

    public EscalatedItemVoteId() {
    }

    public EscalatedItemVoteId(UUID escalatedItemId, UUID participantId) {
        this.escalatedItemId = escalatedItemId;
        this.participantId = participantId;
    }

    public UUID getEscalatedItemId() {
        return escalatedItemId;
    }

    public void setEscalatedItemId(UUID escalatedItemId) {
        this.escalatedItemId = escalatedItemId;
    }

    public UUID getParticipantId() {
        return participantId;
    }

    public void setParticipantId(UUID participantId) {
        this.participantId = participantId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        EscalatedItemVoteId that = (EscalatedItemVoteId) o;
        return Objects.equals(escalatedItemId, that.escalatedItemId)
                && Objects.equals(participantId, that.participantId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(escalatedItemId, participantId);
    }
}
