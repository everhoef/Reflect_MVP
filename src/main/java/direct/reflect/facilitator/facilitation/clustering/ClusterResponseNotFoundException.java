package direct.reflect.facilitator.facilitation.clustering;

import java.util.UUID;

public class ClusterResponseNotFoundException extends RuntimeException {
    public ClusterResponseNotFoundException(UUID responseId) {
        super("Response not found: " + responseId);
    }
}
