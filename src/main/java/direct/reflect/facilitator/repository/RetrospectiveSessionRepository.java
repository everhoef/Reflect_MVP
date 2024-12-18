package direct.reflect.facilitator.repository;

import java.lang.Long;
import org.springframework.data.jpa.repository.JpaRepository;
import direct.reflect.facilitator.entity.RetrospectiveSession;
import java.util.Optional;
import java.util.UUID;


public interface RetrospectiveSessionRepository extends JpaRepository<RetrospectiveSession, Long> {

    Optional<RetrospectiveSession> findByRetroId(UUID retroId);
}
