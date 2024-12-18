package direct.reflect.facilitator.entity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.data.annotation.CreatedDate;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Data
@Table(name = "retro_sessions")
public class RetrospectiveSession {
  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  private Long id;

  @Column(nullable = false, unique = true)
  private UUID retroId;
  @Column(nullable = false)
  private String facilitator;
  @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<Participant> participants = new ArrayList<>();

  private LocalDateTime createdAt;
  private int retrospectivePhase;
  private LocalDateTime finishedAt;

  private void setId(Long id) {
    // Do nothing, just to overwrite Lombok's setter
  }

  public void addParticipant(String username) {
    Participant participant = new Participant();
    participant.setUsername(username);
    participant.setSession(this);
    participant.setLastSeen(LocalDateTime.now());
    participants.add(participant);
  }

  public List<String> getParticipantUsernames() {
    return participants.stream().map(Participant::getUsername).collect(Collectors.toList());
  }

  public Optional<Participant> findParticipant(String username) {
    return participants.stream().filter(p -> p.getUsername().equals(username)).findFirst();
  }

  public List<Participant> getConnectedParticipants() {
    return participants.stream().filter(Participant::isConnected).collect(Collectors.toList());
  }
}
