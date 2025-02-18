package direct.reflect.facilitator.exception;

import java.util.UUID;

public class RetroSessionNotFoundException extends RuntimeException {
  public RetroSessionNotFoundException(UUID retroId) {
    super("Retrospective session with ID " + retroId + " not found.");
  }
}
