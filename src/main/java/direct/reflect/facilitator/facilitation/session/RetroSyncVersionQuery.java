package direct.reflect.facilitator.facilitation.session;

import java.util.UUID;

public interface RetroSyncVersionQuery {

    /**
     * Reads the current authoritative sync version for a retrospective session.
     */
    long getSyncVersion(UUID retroId);
}
