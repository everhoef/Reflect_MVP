package direct.reflect.facilitator.configurator;

public class RetroTemplateNotFoundException extends RuntimeException {
    public RetroTemplateNotFoundException(Long templateId) {
        super("Retrospective Template with ID " + templateId + " not found.");
    }
}
