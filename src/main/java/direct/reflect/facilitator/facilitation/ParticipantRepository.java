package direct.reflect.facilitator.facilitation;

import direct.reflect.facilitator.facilitation.Participant;
import direct.reflect.facilitator.facilitation.ParticipantId;
import direct.reflect.facilitator.facilitation.RetroSession;
import direct.reflect.facilitator.facilitation.ParticipantRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ParticipantRepository extends JpaRepository<Participant, ParticipantId> {
    List<Participant> findBySession_Id(UUID sessionId);
    
    Optional<Participant> findByUsername(String username);
    
    List<Participant> findByParticipantId(UUID participantId); 
    
    @Query("SELECT p FROM Participant p JOIN FETCH p.session WHERE p.participantId = :participantId")
    List<Participant> findByParticipantIdWithSession(@Param("participantId") UUID participantId);
    
    Optional<Participant> findByParticipantIdAndSession_Id(UUID participantId, UUID sessionId);
    
    Optional<Participant> findBySessionAndRoleAndParticipantId(
            RetroSession session, ParticipantRole role, UUID participantId);

    boolean existsBySessionAndParticipantId(RetroSession session, UUID participantId);
}
