package direct.reflect.facilitator.domain.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import direct.reflect.facilitator.domain.enums.RetroPhase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;

@Entity
@Data
@Table(name = "retro_sessions")
public class RetroSession {
  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  private Long id;

  @Column(nullable = false, unique = true)
  private UUID retroId;
  @Column(nullable = false)
  private String facilitator;
  @Column
  private String name;

  private LocalDateTime createdAt;
  private int retrospectivePhase;
  private LocalDateTime finishedAt;

  @ManyToOne(fetch = FetchType.LAZY)
  private RetroTemplate template;
  
  private int currentStepIndex = -1;

  @Enumerated(EnumType.STRING)
  private RetroPhase phase = RetroPhase.CREATED;

  public RetroStage getCurrentStage() {
    return template.getStageForPhase(getPhase());
  }

  private void setId(Long id) {
    // Do nothing, just to overwrite Lombok's setter
  }

  public void advancePhase() {
    setPhase(RetroPhase.values()[getPhase().ordinal() + 1]);
    currentStepIndex = -1;
  }
}
