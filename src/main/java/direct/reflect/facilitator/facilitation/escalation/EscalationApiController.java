package direct.reflect.facilitator.facilitation.escalation;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import direct.reflect.facilitator.facilitation.session.RetroSyncVersionService;
import direct.reflect.facilitator.facilitation.dto.SyncVersionedResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/retro/{retroId}")
@Tag(name = "Escalation API", description = "Participant escalation flagging and voting")
public class EscalationApiController {

    private final EscalationService escalationService;
    private final RetroSyncVersionService retroSyncVersionService;

    public EscalationApiController(
            EscalationService escalationService,
            RetroSyncVersionService retroSyncVersionService) {
        this.escalationService = escalationService;
        this.retroSyncVersionService = retroSyncVersionService;
    }

    @PostMapping("/actions/{actionId}/escalate")
    @PreAuthorize("@participantService.canAccessRetro(#retroId)")
    @ApiResponse(responseCode = "201", description = "Action escalated successfully")
    public ResponseEntity<EscalatedItemDto> escalateAction(
            @PathVariable UUID retroId,
            @PathVariable UUID actionId,
            @Valid @RequestBody EscalateActionRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(escalationService.escalateAction(retroId, actionId, request.problemDescription(), httpRequest));
    }

    @PostMapping("/escalations/{escalationId}/vote")
    @PreAuthorize("@participantService.canAccessRetro(#retroId)")
    public ResponseEntity<EscalationVoteResultDto> toggleVote(
            @PathVariable UUID retroId,
            @PathVariable UUID escalationId,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(escalationService.toggleVote(retroId, escalationId, httpRequest));
    }

    @GetMapping("/escalations")
    @PreAuthorize("@participantService.canAccessRetro(#retroId)")
    public ResponseEntity<SyncVersionedResponse<List<EscalatedItemDto>>> getEscalations(
            @PathVariable UUID retroId,
            HttpServletRequest httpRequest) {
        List<EscalatedItemDto> escalations = escalationService.getEscalations(retroId, httpRequest);
        long syncVersion = retroSyncVersionService.getSyncVersion(retroId);
        return ResponseEntity.ok(new SyncVersionedResponse<>(syncVersion, escalations));
    }
}
