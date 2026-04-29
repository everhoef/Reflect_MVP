package direct.reflect.facilitator.facilitation.participant;

import java.io.Serializable;
import java.util.UUID;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ParticipantId implements Serializable {
    private static final long serialVersionUID = 1L;

    private UUID participantId; // Corresponds to Participant.participantId (stable user ID)
    private UUID session;       // Corresponds to Participant.session.id (PK of RetroSession)
}
