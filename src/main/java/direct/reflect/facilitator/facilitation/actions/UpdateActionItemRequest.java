package direct.reflect.facilitator.facilitation.actions;

import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record UpdateActionItemRequest(
        @Size(max = 500, message = "Action item 'what' must not exceed 500 characters")
        String what,

        @Size(max = 200, message = "Action item 'who' must not exceed 200 characters")
        String who,

        LocalDate dueDate,

        @Size(max = 500, message = "Success criteria must not exceed 500 characters")
        String successCriteria) {

    public boolean hasChanges() {
        return what != null || who != null || dueDate != null || successCriteria != null;
    }
}
