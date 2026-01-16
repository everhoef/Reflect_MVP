package direct.reflect.facilitator.common.exception;

import direct.reflect.facilitator.facilitation.RetroPhase;

/**
 * Exception thrown when attempting to submit a response while the session is not in an active phase.
 * Responses can only be submitted during active retrospective phases (SET_THE_STAGE through CLOSE_RETRO).
 */
public class InvalidSessionStateException extends RuntimeException {
  public InvalidSessionStateException(RetroPhase currentPhase) {
    super("Cannot submit response. Session is in " + currentPhase.getDisplayName() + " phase. " +
          "Responses can only be submitted during active retrospective phases.");
  }
}
