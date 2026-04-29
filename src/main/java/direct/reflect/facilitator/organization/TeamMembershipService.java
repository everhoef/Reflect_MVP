package direct.reflect.facilitator.organization;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TeamMembershipService implements ManagerTeamAccess {

    /** Repository for manager membership lookups. */
    private final TeamMemberRepository teamMemberRepository;

    @Override
    public boolean hasManagerRole(final UUID userId) {
        return teamMemberRepository.existsByUserIdAndRole(userId, TeamRole.MANAGER);
    }

    @Override
    public Optional<UUID> findSingleManagedTeamId(final UUID userId) {
        List<UUID> managedTeamIds = findManagedTeamIds(userId);

        if (managedTeamIds.size() != 1) {
            return Optional.empty();
        }

        return Optional.ofNullable(managedTeamIds.getFirst());
    }

    @Override
    public List<UUID> findManagedTeamIds(final UUID userId) {
        return teamMemberRepository
                .findByUserIdAndRole(userId, TeamRole.MANAGER)
                .stream()
                .map(TeamMember::getTeam)
                .map(Team::getId)
                .toList();
    }
}
