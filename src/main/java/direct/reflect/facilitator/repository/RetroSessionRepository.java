package direct.reflect.facilitator.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import direct.reflect.facilitator.domain.entity.RetroSession;

@Repository
public interface RetroSessionRepository extends JpaRepository<RetroSession, Long> {
    Optional<RetroSession> findByRetroId(UUID retroId);
}
