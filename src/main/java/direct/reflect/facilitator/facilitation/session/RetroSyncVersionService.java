package direct.reflect.facilitator.facilitation.session;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RetroSyncVersionService {

    private final RetroSessionRepository retroSessionRepository;

    public RetroSyncVersionService(RetroSessionRepository retroSessionRepository) {
        this.retroSessionRepository = retroSessionRepository;
    }

    @Transactional
    public long bumpSyncVersion(UUID retroId) {
        RetroSession retroSession = retroSessionRepository.findById(retroId)
                .orElseThrow(() -> new IllegalArgumentException("Retro session not found: " + retroId));

        long nextVersion = currentSyncVersion(retroSession) + 1;
        retroSession.setSyncVersion(nextVersion);
        retroSessionRepository.save(retroSession);
        return nextVersion;
    }

    @Transactional(readOnly = true)
    public long getSyncVersion(UUID retroId) {
        RetroSession retroSession = retroSessionRepository.findById(retroId)
                .orElseThrow(() -> new IllegalArgumentException("Retro session not found: " + retroId));

        return currentSyncVersion(retroSession);
    }

    private long currentSyncVersion(RetroSession retroSession) {
        return retroSession.getSyncVersion() == null ? 0L : retroSession.getSyncVersion();
    }
}
