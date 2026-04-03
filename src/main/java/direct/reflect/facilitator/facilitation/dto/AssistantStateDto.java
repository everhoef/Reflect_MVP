package direct.reflect.facilitator.facilitation.dto;

import java.util.List;

public record AssistantStateDto(

    AssistantMessageDto current,

    List<AssistantMessageDto> history,

    String facilitatorCoachingNote

) {

    public static final int HISTORY_MAX_SIZE = 3;
}
