package direct.reflect.facilitator.configurator;

import direct.reflect.facilitator.configurator.RetroTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RetroTemplateRepository extends JpaRepository<RetroTemplate, Long> {
}
