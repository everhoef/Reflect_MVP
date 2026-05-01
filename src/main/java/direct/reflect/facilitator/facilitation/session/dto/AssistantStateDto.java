package direct.reflect.facilitator.facilitation.session.dto;

import java.util.List;

public record AssistantStateDto(

    AssistantMessageDto current,

    List<AssistantMessageDto> history,

    String facilitatorCoachingNote

) {

    public static final int HISTORY_MAX_SIZE = 3;

    public AssistantStateDto {
        history = history != null ? List.copyOf(history) : null;
    }

    @Override
    public List<AssistantMessageDto> history() {
        return history != null ? List.copyOf(history) : null;
    }
}
