package direct.reflect.facilitator.facilitation;

import java.time.LocalDateTime;
import java.util.UUID;

import direct.reflect.facilitator.facilitation.RetroPhase;
import direct.reflect.facilitator.configurator.RetroTemplate;
import direct.reflect.facilitator.configurator.RetroStage;
import direct.reflect.facilitator.configurator.RetroStep;
import direct.reflect.facilitator.common.entity.GeneratedUuidV7;
import direct.reflect.facilitator.organization.Team;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
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
  private LocalDateTime stepStartedAt;
  private LocalDateTime timerPausedAt;

  private Long accumulatedPauseSeconds = 0L;

  @ManyToOne(fetch = FetchType.LAZY)
  private RetroTemplate template;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "team_id")
  private Team team;

  private int currentStepIndex = -1;

  @Enumerated(EnumType.STRING)
  private RetroPhase phase = RetroPhase.CREATED;

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Team getTeam() {
    return team;
  }

  public void setTeam(Team team) {
    this.team = team;
  }

  public RetroPhase getPhase() {
    return phase;
  }

  public void setPhase(RetroPhase phase) {
    this.phase = phase;
  }

  public RetroStage getCurrentStage() {
    return template.getStageForPhase(phase);
  }

  public boolean isFinished() {
    return phase == RetroPhase.COMPLETED || phase == RetroPhase.ABANDONED;
  }

  public void advancePhase() {
    phase = phase.next();
    currentStepIndex = -1;
  }
}
