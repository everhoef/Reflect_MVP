package direct.reflect.facilitator.facilitation;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RetroSessionRepository extends JpaRepository<RetroSession, UUID> {
}
