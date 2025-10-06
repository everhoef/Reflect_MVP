package direct.reflect.facilitator.configurator;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RetroStepRepository extends JpaRepository<RetroStep, Long> {
    
    List<RetroStep> findByRetroStageOrderByOrderIndexAsc(RetroStage retroStage);
    
    @Query("SELECT rs FROM RetroStep rs WHERE rs.retroStage = :stage AND rs.stepType = :stepType ORDER BY rs.orderIndex")
    List<RetroStep> findByRetroStageAndStepTypeOrderByOrderIndexAsc(
        @Param("stage") RetroStage retroStage, 
        @Param("stepType") StepType stepType
    );
    
    @Query("SELECT rs FROM RetroStep rs WHERE rs.retroStage = :stage AND rs.dataPattern = :pattern ORDER BY rs.orderIndex")
    List<RetroStep> findByRetroStageAndDataPatternOrderByOrderIndexAsc(
        @Param("stage") RetroStage retroStage, 
        @Param("pattern") DataPattern dataPattern
    );
    
    @Query("SELECT MAX(rs.orderIndex) FROM RetroStep rs WHERE rs.retroStage = :stage")
    Integer findMaxOrderIndexByRetroStage(@Param("stage") RetroStage retroStage);
}