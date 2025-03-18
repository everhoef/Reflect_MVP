package direct.reflect.facilitator.repository;

import direct.reflect.facilitator.domain.entity.Participant;
import direct.reflect.facilitator.domain.enums.ParticipantRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ParticipantRepository extends JpaRepository<Participant, Long> {
    List<Participant> findBySession_RetroId(UUID retroId);
    
    Optional<Participant> findByUsername(String username);
    
    Optional<Participant> findByParticipantId(String participantId);
    
    Optional<Participant> findBySession_RetroIdAndRoleAndParticipantId(
            UUID retroId, ParticipantRole role, String participantId);
            
    boolean existsBySession_RetroIdAndParticipantId(UUID retroId, String participantId);
}
