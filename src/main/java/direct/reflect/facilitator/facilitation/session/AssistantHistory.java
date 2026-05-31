package direct.reflect.facilitator.facilitation.session;

import direct.reflect.facilitator.facilitation.session.dto.AssistantMessageDto;
import direct.reflect.facilitator.facilitation.session.dto.AssistantStateDto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class AssistantHistory {

    private final UUID retroId;
    private AssistantMessageDto current;
    private final List<AssistantMessageDto> previousMessages;

    public AssistantHistory(UUID retroId) {
        this.retroId = retroId;
        this.current = null;
        this.previousMessages = new ArrayList<>();
    }

    private AssistantHistory(UUID retroId, AssistantMessageDto current, List<AssistantMessageDto> previous) {
        this.retroId = retroId;
        this.current = current;
        this.previousMessages = new ArrayList<>(previous);
    }

    public static AssistantHistory empty(UUID retroId) {
        return new AssistantHistory(retroId);
    }

    public static AssistantHistory of(UUID retroId, AssistantMessageDto current, List<AssistantMessageDto> previous) {
        AssistantHistory h = new AssistantHistory(retroId, current, previous);
        h.trimHistory();
        return h;
    }

    public void pushMessage(Long stepId, String stepTitle, String publicText) {
        AssistantMessageDto next = new AssistantMessageDto(retroId, stepId, stepTitle, publicText);

        if (current != null) {
            previousMessages.add(0, current);
            trimHistory();
        }

        current = next;
    }

    public AssistantMessageDto current() {
        return current;
    }

    public List<AssistantMessageDto> history() {
        return Collections.unmodifiableList(previousMessages);
    }

    public AssistantStateDto toDto(String facilitatorCoachingNote) {
        return new AssistantStateDto(current, List.copyOf(previousMessages), facilitatorCoachingNote);
    }

    public AssistantStateDto toPublicDto() {
        return new AssistantStateDto(current, List.copyOf(previousMessages), null);
    }

    private void trimHistory() {
        while (previousMessages.size() > AssistantStateDto.HISTORY_MAX_SIZE) {
            previousMessages.remove(previousMessages.size() - 1);
        }
    }
}
