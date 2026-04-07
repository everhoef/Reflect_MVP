package direct.reflect.facilitator.organization;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TeamMemberRepository extends JpaRepository<TeamMember, TeamMemberId> {

    List<TeamMember> findByTeamId(UUID teamId);

    boolean existsByUserIdAndRole(UUID userId, TeamRole role);
}
