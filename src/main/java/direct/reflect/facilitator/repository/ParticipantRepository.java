package direct.reflect.facilitator.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import direct.reflect.facilitator.domain.entity.Participant;
import direct.reflect.facilitator.domain.enums.ParticipantRole;

@Repository
public interface ParticipantRepository extends JpaRepository<Participant, Long> {
    List<Participant> findBySession_RetroId(UUID retroId);
    Optional<Participant> findBySession_RetroIdAndRole(UUID retroId, ParticipantRole role);
    Optional<Participant> findByUsername(String username);
    boolean existsBySession_RetroIdAndUsername(UUID retroId, String username);
}
