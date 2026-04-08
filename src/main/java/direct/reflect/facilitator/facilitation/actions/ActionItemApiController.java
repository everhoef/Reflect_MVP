package direct.reflect.facilitator.facilitation.actions;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/retro/{retroId}/actions")
@RequiredArgsConstructor
@Tag(name = "Action Item API", description = "SMART action item CRUD operations")
public class ActionItemApiController {

    private final ActionItemService actionItemService;

    @PostMapping
    @PreAuthorize("@participantService.canAccessRetro(#retroId)")
    public ResponseEntity<ActionItemDto> createActionItem(
            @PathVariable UUID retroId,
            @Valid @RequestBody CreateActionItemRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(actionItemService.createActionItem(retroId, request, httpRequest));
    }

    @GetMapping
    @PreAuthorize("@participantService.canAccessRetro(#retroId)")
    public ResponseEntity<List<ActionItemDto>> getActionItems(@PathVariable UUID retroId) {
        return ResponseEntity.ok(actionItemService.getActionItems(retroId));
    }

    @PatchMapping("/{actionId}")
    @PreAuthorize("@participantService.canAccessRetro(#retroId)")
    public ResponseEntity<ActionItemDto> updateActionItem(
            @PathVariable UUID retroId,
            @PathVariable UUID actionId,
            @Valid @RequestBody UpdateActionItemRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(actionItemService.updateActionItem(retroId, actionId, request, httpRequest));
    }

    @DeleteMapping("/{actionId}")
    @PreAuthorize("@participantService.canAccessRetro(#retroId)")
    public ResponseEntity<Void> deleteActionItem(
            @PathVariable UUID retroId,
            @PathVariable UUID actionId,
            HttpServletRequest httpRequest) {
        actionItemService.deleteActionItem(retroId, actionId, httpRequest);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{actionId}/status")
    @PreAuthorize("@participantService.canAccessRetro(#retroId)")
    public ResponseEntity<ActionItemDto> updateActionItemStatus(
            @PathVariable UUID retroId,
            @PathVariable UUID actionId,
            @Valid @RequestBody UpdateActionItemStatusRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(actionItemService.updateActionItemStatus(retroId, actionId, request, httpRequest));
    }
}
