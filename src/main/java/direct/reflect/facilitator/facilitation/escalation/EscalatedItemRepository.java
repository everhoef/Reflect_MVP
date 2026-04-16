package direct.reflect.facilitator.facilitation.escalation;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EscalatedItemRepository extends JpaRepository<EscalatedItem, UUID> {
}
