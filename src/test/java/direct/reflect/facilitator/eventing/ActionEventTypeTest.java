package direct.reflect.facilitator.eventing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Action and Escalation SSE Event Type Stability Test")
class ActionEventTypeTest {

    @Test
    @DisplayName("Event type surface matches the supported SSE contract")
    void eventTypeSurface_matchesSupportedSseContract() {
        assertThat(RetroEvent.EventType.values())
                .extracting(Enum::name)
                .containsExactly(
                        "PARTICIPANT_JOINED",
                        "PARTICIPANT_LEFT",
                        "SESSION_STARTED",
                        "STEP_ADVANCED",
                        "RETRO_CREATED",
                        "PHASE_STARTED",
                        "NOTE_ADDED",
                        "NOTE_UPDATED",
                        "NOTE_DELETED",
                        "VOTE_ADDED",
                        "VOTE_REMOVED",
                        "GROUP_CREATED",
                        "GROUP_UPDATED",
                        "GROUP_DELETED",
                        "ACTION_CREATED",
                        "ACTION_UPDATED",
                        "ACTION_DELETED",
                        "ESCALATION_CREATED",
                        "ESCALATION_VOTE_UPDATED",
                        "TIMER_STARTED",
                        "TIMER_PAUSED",
                        "TIMER_FINISHED");
    }
}
