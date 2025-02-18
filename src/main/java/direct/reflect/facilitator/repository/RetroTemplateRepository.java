package direct.reflect.facilitator.repository;

import direct.reflect.facilitator.domain.entity.RetroTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RetroTemplateRepository extends JpaRepository<RetroTemplate, Long> {
}
