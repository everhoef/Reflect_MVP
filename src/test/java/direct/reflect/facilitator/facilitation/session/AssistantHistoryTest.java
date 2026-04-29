package direct.reflect.facilitator.facilitation.session;

import direct.reflect.facilitator.facilitation.session.AssistantHistory;
import direct.reflect.facilitator.facilitation.session.dto.AssistantMessageDto;
import direct.reflect.facilitator.facilitation.session.dto.AssistantStateDto;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AssistantHistoryTest {

    private final UUID retroId = UUID.randomUUID();

    @Test
    void emptyHistory_hasNullCurrentAndEmptyList() {
        AssistantHistory history = AssistantHistory.empty(retroId);

        assertThat(history.current()).isNull();
        assertThat(history.history()).isEmpty();
    }

    @Test
    void pushFirstMessage_becomesCurrentWithNoHistory() {
        AssistantHistory history = AssistantHistory.empty(retroId);
        history.pushMessage(1L, "Step One", "Welcome to the retrospective.");

        assertThat(history.current()).isNotNull();
        assertThat(history.current().publicText()).isEqualTo("Welcome to the retrospective.");
        assertThat(history.current().stepId()).isEqualTo(1L);
        assertThat(history.history()).isEmpty();
    }

    @Test
    void pushSecondMessage_firstMovesToHistory() {
        AssistantHistory history = AssistantHistory.empty(retroId);
        history.pushMessage(1L, "Step One", "First message.");
        history.pushMessage(2L, "Step Two", "Second message.");

        assertThat(history.current().stepId()).isEqualTo(2L);
        assertThat(history.history()).hasSize(1);
        assertThat(history.history().get(0).stepId()).isEqualTo(1L);
    }

    @Test
    void pushFourMessages_historyHoldsExactlyThree() {
        AssistantHistory history = AssistantHistory.empty(retroId);
        history.pushMessage(1L, "Step One", "Msg 1");
        history.pushMessage(2L, "Step Two", "Msg 2");
        history.pushMessage(3L, "Step Three", "Msg 3");
        history.pushMessage(4L, "Step Four", "Msg 4");

        assertThat(history.current().stepId()).isEqualTo(4L);
        assertThat(history.history()).hasSize(3);
        assertThat(history.history().get(0).stepId()).isEqualTo(3L);
        assertThat(history.history().get(1).stepId()).isEqualTo(2L);
        assertThat(history.history().get(2).stepId()).isEqualTo(1L);
    }

    @Test
    void pushFiveMessages_oldestIsDroppedAndCapStaysAtThree() {
        AssistantHistory history = AssistantHistory.empty(retroId);
        for (int i = 1; i <= 5; i++) {
            history.pushMessage((long) i, "Step " + i, "Msg " + i);
        }

        assertThat(history.current().stepId()).isEqualTo(5L);
        assertThat(history.history()).hasSize(3);

        assertThat(history.history().get(0).stepId()).isEqualTo(4L);
        assertThat(history.history().get(1).stepId()).isEqualTo(3L);
        assertThat(history.history().get(2).stepId()).isEqualTo(2L);
    }

    @Test
    void pushTenMessages_capNeverExceedsThree() {
        AssistantHistory history = AssistantHistory.empty(retroId);
        for (int i = 1; i <= 10; i++) {
            history.pushMessage((long) i, "Step " + i, "Msg " + i);
        }

        assertThat(history.history().size()).isLessThanOrEqualTo(AssistantStateDto.HISTORY_MAX_SIZE);
    }

    @Test
    void toDto_withCoachingNote_includesPrivateNote() {
        AssistantHistory history = AssistantHistory.empty(retroId);
        history.pushMessage(1L, "Step One", "Public message.");

        AssistantStateDto dto = history.toDto("Private tip for facilitator.");

        assertThat(dto.current().publicText()).isEqualTo("Public message.");
        assertThat(dto.facilitatorCoachingNote()).isEqualTo("Private tip for facilitator.");
    }

    @Test
    void toPublicDto_omitsFacilitatorCoachingNote() {
        AssistantHistory history = AssistantHistory.empty(retroId);
        history.pushMessage(1L, "Step One", "Public message.");

        AssistantStateDto dto = history.toPublicDto();

        assertThat(dto.current().publicText()).isEqualTo("Public message.");
        assertThat(dto.facilitatorCoachingNote()).isNull();
    }

    @Test
    void historyOrder_isNewestFirst() {
        AssistantHistory history = AssistantHistory.empty(retroId);
        history.pushMessage(1L, "Step One", "Msg 1");
        history.pushMessage(2L, "Step Two", "Msg 2");
        history.pushMessage(3L, "Step Three", "Msg 3");

        assertThat(history.history().get(0).stepId()).isEqualTo(2L);
        assertThat(history.history().get(1).stepId()).isEqualTo(1L);
    }

    @Test
    void ofFactory_clampsExcessivePreviousListToHistoryCap() {
        java.util.List<AssistantMessageDto> tooMany = new java.util.ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            tooMany.add(new AssistantMessageDto(retroId, (long) i, "Step " + i, "Msg " + i));
        }
        AssistantMessageDto current = new AssistantMessageDto(retroId, 11L, "Step 11", "Current");

        AssistantHistory history = AssistantHistory.of(retroId, current, tooMany);

        assertThat(history.history()).hasSize(AssistantStateDto.HISTORY_MAX_SIZE);
    }

    @Test
    void retroId_isConsistentAcrossAllMessages() {
        AssistantHistory history = AssistantHistory.empty(retroId);
        history.pushMessage(1L, "Step One", "Msg 1");
        history.pushMessage(2L, "Step Two", "Msg 2");

        assertThat(history.current().retroId()).isEqualTo(retroId);
        assertThat(history.history().get(0).retroId()).isEqualTo(retroId);
    }
}
