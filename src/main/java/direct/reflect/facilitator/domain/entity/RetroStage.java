package direct.reflect.facilitator.domain.entity;

import jakarta.persistence.*;

import java.time.Duration;

import direct.reflect.facilitator.domain.enums.RetroPhase;
import lombok.Data;

@Entity
@Data
public class RetroStage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RetroPhase phase;
    
    private String name;    // e.g., "Mad, Sad, Glad"
    private String why;     // Purpose of this stage
    private String what;    // What will happen
    private Duration duration; // Expected duration

}
