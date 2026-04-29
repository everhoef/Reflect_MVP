package direct.reflect.facilitator.organization;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ManagerTeamAccess {

    /**
     * Checks whether the given user manages at least one team.
     */
    boolean hasManagerRole(UUID userId);

    /**
     * Returns the only managed team id when the user manages exactly one team.
     */
    Optional<UUID> findSingleManagedTeamId(UUID userId);

    /**
     * Returns every team id managed by the given user.
     */
    List<UUID> findManagedTeamIds(UUID userId);
}
