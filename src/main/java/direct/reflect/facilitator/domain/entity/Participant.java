package direct.reflect.facilitator.domain.entity;

import java.time.LocalDateTime;
import jakarta.persistence.*;
import lombok.Data;
import direct.reflect.facilitator.domain.enums.ParticipantRole;

@Entity
@Table(name = "participants")
@Data
public class Participant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * Stable identifier (UUIDs) for participants
     */
    @Column(name = "participant_id", nullable = false, unique = true)
    private String participantId;
    
    /**
     * Username for authentication purposes
     */
    @Column(name = "username")
    private String username;
    
    /**
     * User-selected display name
     */
    @Column(name = "display_name", nullable = false)
    private String displayName;
    
    @ManyToOne
    @JoinColumn(name = "session_id")
    private RetroSession session;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    private ParticipantRole role = ParticipantRole.PARTICIPANT;
    
    @Column(name = "last_seen")
    private LocalDateTime lastSeen;
}
