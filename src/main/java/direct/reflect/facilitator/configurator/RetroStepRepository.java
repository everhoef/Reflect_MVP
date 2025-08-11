package direct.reflect.facilitator.configurator;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import direct.reflect.facilitator.configurator.RetroStep;

@Repository
public interface RetroStepRepository extends JpaRepository<RetroStep, Long> {
    List<RetroStep> findByStageId(Long stageId);
}