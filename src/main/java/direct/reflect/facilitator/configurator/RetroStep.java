package direct.reflect.facilitator.configurator;

import jakarta.persistence.*;
import lombok.Data;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.Map;

@Entity
@Data
public class RetroStep {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "retro_stage_id", nullable = false)
    private RetroStage retroStage;

    @Column(nullable = false)
    private Integer orderIndex;

    @Column(nullable = false)
    private Integer durationSeconds = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StepType stepType;

    @Enumerated(EnumType.STRING)
    private DataPattern dataPattern;

    @Column(nullable = false)
    private String title = "";

    @Column(columnDefinition = "TEXT", nullable = false)
    private String configuration = "{}";
    
    @Transient
    private String parsedContent;

    @Transient
    private Map<String, Object> parsedConfigMap;

    @PostLoad
    public void parseConfiguration() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> config = mapper.readValue(configuration, new TypeReference<Map<String, Object>>() {});
            this.parsedConfigMap = config;
            this.parsedContent = (String) config.get("content");
        } catch (Exception e) {
            // Fallback to raw configuration if parsing fails
            this.parsedConfigMap = Map.of();
            this.parsedContent = configuration;
        }
    }

    /**
     * Get parsed configuration as a Map for template access.
     * For POC - provides direct access to configuration fields like categories, scale, etc.
     */
    public Map<String, Object> getConfig() {
        if (parsedConfigMap == null) {
            parseConfiguration();
        }
        return parsedConfigMap != null ? parsedConfigMap : Map.of();
    }
}
