package direct.reflect.facilitator.facilitation.actions;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import direct.reflect.facilitator.facilitation.session.RetroSyncVersionService;
import direct.reflect.facilitator.facilitation.dto.SyncVersionedResponse;
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
@RequestMapping("/api/retros/{retroId}/actions")
@Tag(name = "Action Item API", description = "SMART action item CRUD operations")
@RequiredArgsConstructor
public class ActionItemApiController {

    private final ActionItemService actionItemService;
    private final RetroSyncVersionService retroSyncVersionService;

    @PostMapping
    @PreAuthorize("@participantService.canAccessRetro(#retroId)")
    @ApiResponse(responseCode = "201", description = "Action item created successfully")
    public ResponseEntity<ActionItemDto> createActionItem(
            @PathVariable UUID retroId,
            @Valid @RequestBody CreateActionItemRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(actionItemService.createActionItem(retroId, request, httpRequest));
    }

    @GetMapping
    @PreAuthorize("@participantService.canAccessRetro(#retroId)")
    public ResponseEntity<SyncVersionedResponse<List<ActionItemDto>>> getActionItems(@PathVariable UUID retroId) {
        List<ActionItemDto> actionItems = actionItemService.getActionItems(retroId);
        long syncVersion = retroSyncVersionService.getSyncVersion(retroId);
        return ResponseEntity.ok(new SyncVersionedResponse<>(syncVersion, actionItems));
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
    @ApiResponse(responseCode = "204", description = "Action item deleted successfully")
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
