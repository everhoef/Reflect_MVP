
package direct.reflect.facilitator.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "participants")
public class Participant {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String username;

  private String sseClientId;
  private LocalDateTime lastSeen;
  private boolean isConnected;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "retro_session_id")
  private RetrospectiveSession session;
}
