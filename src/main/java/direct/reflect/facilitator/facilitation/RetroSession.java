package direct.reflect.facilitator.facilitation;

import java.time.LocalDateTime;
import java.util.UUID;

import direct.reflect.facilitator.facilitation.RetroPhase;
import direct.reflect.facilitator.configurator.RetroTemplate;
import direct.reflect.facilitator.configurator.RetroStage;
import direct.reflect.facilitator.common.entity.GeneratedUuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.FetchType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;

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

  @ManyToOne(fetch = FetchType.LAZY)
  private RetroTemplate template;
  
  private int currentStepIndex = -1;

  @Enumerated(EnumType.STRING)
  private RetroPhase phase = RetroPhase.CREATED;

  public RetroStage getCurrentStage() {
    return template.getStageForPhase(getPhase());
  }

  public boolean isFinished() {
    return getPhase() == RetroPhase.COMPLETED || getPhase() == RetroPhase.ABANDONED;
  }

  public void advancePhase() {
    setPhase(RetroPhase.values()[getPhase().ordinal() + 1]);
    currentStepIndex = -1;
  }
}
