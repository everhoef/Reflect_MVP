package direct.reflect.facilitator.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import direct.reflect.facilitator.domain.entity.Participant;
import direct.reflect.facilitator.domain.entity.RetroSession;
import direct.reflect.facilitator.domain.enums.ParticipantRole;
import direct.reflect.facilitator.repository.ParticipantRepository;
import direct.reflect.facilitator.repository.RetroSessionRepository;

@Service
@RequiredArgsConstructor
public class ParticipantService {
    private final ParticipantRepository participantRepository;
    private final RetroSessionRepository retroSessionRepository;

    public void addFacilitator(UUID retroId) {
        RetroSession session = retroSessionRepository.findByRetroId(retroId)
            .orElseThrow(() -> new IllegalStateException("Retro session not found"));
            
        Participant facilitator = new Participant();
        facilitator.setRole(ParticipantRole.FACILITATOR);
        facilitator.setSession(session);
        facilitator.setUsername(getCurrentUsername());
        participantRepository.save(facilitator);
    }

    public void addParticipant(UUID retroId, String nickname) {
        RetroSession session = retroSessionRepository.findByRetroId(retroId)
            .orElseThrow(() -> new IllegalStateException("Retro session not found"));
            
        Participant participant = new Participant();
        participant.setUsername(nickname);
        participant.setLastSeen(LocalDateTime.now());
        participant.setSession(session);
        participantRepository.save(participant);
    }

    public List<Participant> getSessionParticipants(UUID retroId) {
        return participantRepository.findBySession_RetroId(retroId);
    }

    public boolean isFacilitator(UUID retroId) {
        return participantRepository.findBySession_RetroIdAndRole(retroId, ParticipantRole.FACILITATOR)
            .isPresent();
    }

    public boolean isParticipating(UUID retroId) {
        return participantRepository.existsBySession_RetroIdAndUsername(retroId, getCurrentUsername());
    }

    public Participant getCurrentParticipant() {
        String username = getCurrentUsername();
        return participantRepository.findByUsername(username)
            .orElseThrow(() -> new IllegalStateException("Current user not found"));
    }

    private String getCurrentUsername() {
        // Get from Spring Security context
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}
