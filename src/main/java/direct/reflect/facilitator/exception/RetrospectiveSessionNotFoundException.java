package direct.reflect.facilitator.exception;

import java.util.UUID;

public class RetrospectiveSessionNotFoundException extends RuntimeException {
  public RetrospectiveSessionNotFoundException(UUID retroId) {
    super("Retrospective session with ID " + retroId + " not found.");
  }
}
