package direct.reflect.facilitator.web.dto;

import lombok.Builder;
import lombok.Data;
import lombok.Singular;

import java.util.List;
import java.util.Map;

/**
 * Activity-specific template data for different data patterns
 */
@Data
@Builder
public class ActivityTemplateData {
    private ActivityType type;
    private Map<String, Object> data;
    
    public enum ActivityType {
        CATEGORICAL, RATING, FREEFORM
    }
    
    /**
     * Categorical activity data
     */
    @Data
    @Builder
    public static class CategoricalData {
        @Singular
        private List<Category> categories;
        private Integer maxLength;
        private boolean allowMultiple;
        
        @Data
        @Builder
        public static class Category {
            private String id;
            private String title;
            private String emoji;
            private String color;
        }
    }
    
    /**
     * Rating activity data
     */
    @Data
    @Builder
    public static class RatingData {
        private Scale scale;
        private List<String> labels;
        private boolean allowComment;
        
        @Data
        @Builder
        public static class Scale {
            private int min;
            private int max;
            private int step;
        }
    }
    
    /**
     * Freeform activity data
     */
    @Data
    @Builder
    public static class FreeformData {
        private String prompt;
        private Integer maxLength;
        private boolean allowMultiple;
    }
    
    // Factory methods for type safety
    public static ActivityTemplateData categorical(CategoricalData data) {
        return ActivityTemplateData.builder()
                .type(ActivityType.CATEGORICAL)
                .data(Map.of("categorical", data))
                .build();
    }
    
    public static ActivityTemplateData rating(RatingData data) {
        return ActivityTemplateData.builder()
                .type(ActivityType.RATING)
                .data(Map.of("rating", data))
                .build();
    }
    
    public static ActivityTemplateData freeform(FreeformData data) {
        return ActivityTemplateData.builder()
                .type(ActivityType.FREEFORM)
                .data(Map.of("freeform", data))
                .build();
    }
    
    // Convenience getters
    public CategoricalData getCategorical() {
        return (CategoricalData) data.get("categorical");
    }
    
    public RatingData getRating() {
        return (RatingData) data.get("rating");
    }
    
    public FreeformData getFreeform() {
        return (FreeformData) data.get("freeform");
    }
}