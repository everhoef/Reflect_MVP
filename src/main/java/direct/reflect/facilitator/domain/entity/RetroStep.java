package direct.reflect.facilitator.domain.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "step_type")
@Data
public abstract class RetroStep {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Integer orderIndex;  // Position within stage
    private Integer durationSeconds;  // Expected duration

    @ManyToOne(fetch = FetchType.LAZY)
    private RetroStage stage;
}
