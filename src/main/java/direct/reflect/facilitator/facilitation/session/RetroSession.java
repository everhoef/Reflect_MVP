package direct.reflect.facilitator.facilitation.session;

import direct.reflect.facilitator.common.ids.GeneratedUuidV7;
import direct.reflect.facilitator.configurator.RetroStage;
import direct.reflect.facilitator.configurator.RetroTemplate;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Data;

@Entity
@Data
@Table(name = "retro_sessions")
public class RetroSession {

  @Id
  @GeneratedUuidV7 // Use the custom annotation for UUIDv7 generation
  @Column(name = "id", nullable = false, unique = true, updatable = false)
  private UUID id;

  @Column
  private String name;

  private LocalDateTime createdAt;
  private LocalDateTime finishedAt;
  private LocalDateTime stepStartedAt;
  private LocalDateTime timerPausedAt;

  private Long accumulatedPauseSeconds = 0L;

  @ManyToOne(fetch = FetchType.LAZY)
  private RetroTemplate template;

  @Column(name = "team_id")
  private UUID teamId;

  private int currentStepIndex = -1;

  @Column(nullable = false)
  private Long syncVersion = 0L;

  @Enumerated(EnumType.STRING)
  private RetroPhase phase = RetroPhase.CREATED;

  public RetroStage getCurrentStage() {
    return getStageForPhase(phase);
  }

  public RetroStage getStageForPhase(RetroPhase targetPhase) {
    if (template == null || targetPhase == null) {
      return null;
    }

    return switch (targetPhase) {
      case SET_THE_STAGE -> template.getSetTheStage();
      case GATHER_DATA -> template.getGatherData();
      case GENERATE_INSIGHTS -> template.getGenerateInsights();
      case DECIDE_ACTIONS -> template.getDecideActions();
      case CLOSE_RETRO -> template.getCloseRetro();
      case LOBBY, CREATED, PAUSED, COMPLETED, ABANDONED -> null;
    };
  }

  public UUID getTeamId() {
    return teamId;
  }

  public boolean isFinished() {
    return phase == RetroPhase.COMPLETED || phase == RetroPhase.ABANDONED;
  }

  public void advancePhase() {
    phase = phase.next();
    currentStepIndex = -1;
  }
}
