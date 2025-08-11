package direct.reflect.facilitator.facilitation;

import java.io.Serializable;
import java.util.UUID;
import java.util.Objects;

public class ParticipantId implements Serializable {
    private static final long serialVersionUID = 1L;

    private UUID participantId; // Corresponds to Participant.participantId (stable user ID)
    private UUID session;       // Corresponds to Participant.session.id (PK of RetroSession)

    public ParticipantId() {
    }

    public ParticipantId(UUID participantId, UUID session) {
        this.participantId = participantId;
        this.session = session;
    }

    public UUID getParticipantId() {
        return participantId;
    }

    public void setParticipantId(UUID participantId) {
        this.participantId = participantId;
    }

    public UUID getSession() {
        return session;
    }

    public void setSession(UUID session) {
        this.session = session;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ParticipantId that = (ParticipantId) o;
        return Objects.equals(participantId, that.participantId) &&
               Objects.equals(session, that.session);
    }

    @Override
    public int hashCode() {
        return Objects.hash(participantId, session);
    }
}
