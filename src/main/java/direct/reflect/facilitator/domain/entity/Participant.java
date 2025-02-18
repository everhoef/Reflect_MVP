package direct.reflect.facilitator.domain.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

import direct.reflect.facilitator.domain.enums.ParticipantRole;

@Entity
@Data
@Table(name = "participants")
public class Participant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String username;
    
    private LocalDateTime lastSeen;
    private boolean isConnected;
    private boolean isAuthenticated;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ParticipantRole role = ParticipantRole.PARTICIPANT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "retro_session_id")
    private RetroSession session;
}
