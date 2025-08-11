package direct.reflect.facilitator.configurator;

import java.time.Duration;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;

@Entity
@Data
public class RetroStage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Integer mastersheetID; // Unique ID from the mastersheet, used for import/export
    private String name;    // e.g., "Mad, Sad, Glad"
    private String why;     // Purpose of this stage
    private String what;    // What will happen
    private Duration duration; // Expected duration

    // Overwrite setter for id
    public void setId(Long id) {
        return; // No-op to prevent setting id directly
    }
}
