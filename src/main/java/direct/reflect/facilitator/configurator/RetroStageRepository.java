package direct.reflect.facilitator.configurator;

import direct.reflect.facilitator.configurator.RetroStage;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RetroStageRepository extends JpaRepository<RetroStage, Long> {

    Optional<RetroStage> findByMastersheetID(Integer mastersheetID);
    
}
