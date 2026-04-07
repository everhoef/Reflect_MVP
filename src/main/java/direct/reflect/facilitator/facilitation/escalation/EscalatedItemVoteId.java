package direct.reflect.facilitator.facilitation.escalation;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class EscalatedItemVoteId implements Serializable {
    private static final long serialVersionUID = 1L;

    @Column(name = "escalated_item_id", nullable = false)
    private UUID escalatedItemId;

    @Column(name = "participant_id", nullable = false)
    private UUID participantId;
}
