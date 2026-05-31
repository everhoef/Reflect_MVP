package direct.reflect.facilitator.configurator;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RetroStepQueryService {

  private final RetroStepRepository retroStepRepository;

  public List<RetroStep> findStepsByStage(final RetroStage retroStage) {
    return retroStepRepository.findByRetroStageOrderByOrderIndexAsc(retroStage);
  }

  public List<RetroStep> findStepsByStageAndComponentType(
      final RetroStage retroStage,
      final ComponentType componentType) {
    return retroStepRepository.findByRetroStageAndComponentType(
        retroStage,
        componentType);
  }

  public RetroStep getStepById(final Long stepId) {
    return retroStepRepository.findById(stepId)
        .orElseThrow(() -> new IllegalArgumentException(
            "RetroStep not found with ID: " + stepId));
  }
}
