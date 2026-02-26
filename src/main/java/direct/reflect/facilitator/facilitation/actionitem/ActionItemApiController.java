package direct.reflect.facilitator.facilitation.actionitem;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/retro/{retroId}")
@RequiredArgsConstructor
@Slf4j
public class ActionItemApiController {

    private final ActionItemService actionItemService;

    @PostMapping("/step/{stepId}/action-items")
    @PreAuthorize("@participantService.canAccessRetro(#retroId)")
    public ResponseEntity<ActionItemDto> createActionItem(
            @PathVariable UUID retroId,
            @PathVariable Long stepId,
            @RequestBody @Valid ActionItemDto dto) {
        ActionItemDto created = actionItemService.createActionItem(retroId, stepId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/action-items/{id}")
    @PreAuthorize("@participantService.canAccessRetro(#retroId)")
    public ResponseEntity<ActionItemDto> updateActionItem(
            @PathVariable UUID retroId,
            @PathVariable UUID id,
            @RequestBody @Valid ActionItemDto dto) {
        ActionItemDto updated = actionItemService.updateActionItem(retroId, id, dto);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/action-items/{id}")
    @PreAuthorize("@participantService.canAccessRetro(#retroId)")
    public ResponseEntity<Void> deleteActionItem(
            @PathVariable UUID retroId,
            @PathVariable UUID id) {
        actionItemService.deleteActionItem(retroId, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/action-items")
    @PreAuthorize("@participantService.canAccessRetro(#retroId)")
    public ResponseEntity<List<ActionItemDto>> getActionItemsBySession(
            @PathVariable UUID retroId) {
        List<ActionItemDto> items = actionItemService.getActionItemsBySession(retroId);
        return ResponseEntity.ok(items);
    }
}
