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
public class TeamMembershipService {

    private final TeamMemberRepository teamMemberRepository;

    public boolean hasManagerRole(UUID userId) {
        return teamMemberRepository.existsByUserIdAndRole(userId, TeamRole.MANAGER);
    }

    public Optional<Team> findSingleManagedTeam(UUID userId) {
        List<Team> managedTeams = teamMemberRepository.findByUserIdAndRole(userId, TeamRole.MANAGER).stream()
                .map(TeamMember::getTeam)
                .toList();

        if (managedTeams.size() != 1) {
            return Optional.empty();
        }

        return Optional.ofNullable(managedTeams.getFirst());
    }
}
