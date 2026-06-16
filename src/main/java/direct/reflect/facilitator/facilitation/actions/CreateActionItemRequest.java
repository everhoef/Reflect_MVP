package direct.reflect.facilitator.facilitation.actions;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CreateActionItemRequest(
        @NotBlank(message = "Action item 'what' is required")
        @Size(max = 500, message = "Action item 'what' must not exceed 500 characters")
        String what,

        @Size(max = 200, message = "Action item 'who' must not exceed 200 characters")
        String who,

        @NotNull(message = "Action item due date is required")
        LocalDate dueDate,

        @NotBlank(message = "Success criteria is required")
        @Size(max = 500, message = "Success criteria must not exceed 500 characters")
        String successCriteria) {
}
