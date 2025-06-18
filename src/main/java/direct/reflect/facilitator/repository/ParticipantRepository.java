package direct.reflect.facilitator.repository;

import direct.reflect.facilitator.domain.entity.Participant;
import direct.reflect.facilitator.domain.entity.ParticipantId;
import direct.reflect.facilitator.domain.entity.RetroSession;
import direct.reflect.facilitator.domain.enums.ParticipantRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ParticipantRepository extends JpaRepository<Participant, ParticipantId> {
    List<Participant> findBySession_Id(UUID sessionId);
    
    Optional<Participant> findByUsername(String username);
    
    List<Participant> findByParticipantId(UUID participantId); 
    
    Optional<Participant> findByParticipantIdAndSession_Id(UUID participantId, UUID sessionId);
    
    Optional<Participant> findBySessionAndRoleAndParticipantId(
            RetroSession session, ParticipantRole role, UUID participantId);

    boolean existsBySessionAndParticipantId(RetroSession session, UUID participantId);
}
