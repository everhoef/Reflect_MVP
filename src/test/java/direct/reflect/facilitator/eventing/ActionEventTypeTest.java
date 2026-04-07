package direct.reflect.facilitator.eventing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Action and Escalation SSE Event Type Stability Test")
class ActionEventTypeTest {

    @Test
    @DisplayName("Required action and escalation event types are present")
    void requiredEventTypes_arePresent() {
        assertThat(RetroEvent.EventType.valueOf("ACTION_CREATED")).isEqualTo(RetroEvent.EventType.ACTION_CREATED);
        assertThat(RetroEvent.EventType.valueOf("ACTION_UPDATED")).isEqualTo(RetroEvent.EventType.ACTION_UPDATED);
        assertThat(RetroEvent.EventType.valueOf("ACTION_DELETED")).isEqualTo(RetroEvent.EventType.ACTION_DELETED);
        assertThat(RetroEvent.EventType.valueOf("ESCALATION_VOTE_UPDATED")).isEqualTo(RetroEvent.EventType.ESCALATION_VOTE_UPDATED);
    }

    @Test
    @DisplayName("Representative existing event types remain stable")
    void existingEventTypes_remainStable() {
        assertThat(RetroEvent.EventType.valueOf("PARTICIPANT_JOINED")).isEqualTo(RetroEvent.EventType.PARTICIPANT_JOINED);
        assertThat(RetroEvent.EventType.valueOf("NOTE_ADDED")).isEqualTo(RetroEvent.EventType.NOTE_ADDED);
        assertThat(RetroEvent.EventType.valueOf("VOTE_REMOVED")).isEqualTo(RetroEvent.EventType.VOTE_REMOVED);
    }
}
