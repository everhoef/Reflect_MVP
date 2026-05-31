package direct.reflect.facilitator.facilitation.clustering;

import java.util.UUID;

public class ClusterNotFoundException extends RuntimeException {
    public ClusterNotFoundException(UUID clusterId) {
        super("Cluster not found: " + clusterId);
    }
}
