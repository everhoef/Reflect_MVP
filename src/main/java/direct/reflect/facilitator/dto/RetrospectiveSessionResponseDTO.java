package direct.reflect.facilitator.dto;

import lombok.Data;
import java.util.UUID;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class RetrospectiveSessionResponseDTO {
  private UUID id;
  private String facilitator;
  private List<String> participants;
  private LocalDateTime createdAt;
}
