package direct.reflect.facilitator.facilitation.actions;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/retros/{retroId}/actions")
@RequiredArgsConstructor
@Tag(name = "Action Carry-Over API", description = "Previous session action item lookup")
public class ActionCarryOverApiController {

  private final ActionItemCarryOverService actionItemCarryOverService;

    @GetMapping("/previous")
  @PreAuthorize("@participantService.canAccessRetro(#retroId)")
  public ResponseEntity<List<ActionItemDto>> getPreviousActions(@PathVariable UUID retroId) {
    return ResponseEntity.ok(actionItemCarryOverService.getPreviousOpenActions(retroId));
  }
}
