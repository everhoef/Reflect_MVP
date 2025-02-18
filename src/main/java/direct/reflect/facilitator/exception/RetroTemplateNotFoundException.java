package direct.reflect.facilitator.exception;

public class RetroTemplateNotFoundException extends RuntimeException {
    public RetroTemplateNotFoundException(Long templateId) {
        super("Retrospective Template with ID " + templateId + " not found.");
    }
}
