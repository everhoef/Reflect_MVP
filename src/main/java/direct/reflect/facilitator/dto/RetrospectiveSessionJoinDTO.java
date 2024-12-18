package direct.reflect.facilitator.dto;

import lombok.Data;
import java.util.UUID;
import jakarta.validation.constraints.NotBlank;

@Data
public class RetrospectiveSessionJoinDTO {
  @NotBlank(message = "Retrospective ID is required")
  private UUID id;
  @NotBlank(message = "User Name is required")
  private String username;
}
