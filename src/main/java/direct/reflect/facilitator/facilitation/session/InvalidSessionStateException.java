package direct.reflect.facilitator.facilitation.session;

import direct.reflect.facilitator.facilitation.session.RetroPhase;

public class InvalidSessionStateException extends RuntimeException {
    public InvalidSessionStateException(RetroPhase currentPhase) {
        super("Cannot submit response. Session is in " + currentPhase.getDisplayName() + " phase. "
            + "Responses can only be submitted during active retrospective phases.");
    }
}
