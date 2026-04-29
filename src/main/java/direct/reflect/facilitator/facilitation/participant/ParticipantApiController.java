package direct.reflect.facilitator.facilitation.participant;

import direct.reflect.facilitator.facilitation.participant.dto.JoinRetroRequest;
import direct.reflect.facilitator.facilitation.participant.dto.JoinRetroResponse;
import direct.reflect.facilitator.facilitation.participant.dto.LeaveActiveSessionsResult;
import direct.reflect.facilitator.facilitation.participant.dto.ParticipantDto;
import direct.reflect.facilitator.facilitation.participant.dto.SessionInfo;
import direct.reflect.facilitator.facilitation.session.RetroSession;
import direct.reflect.facilitator.facilitation.session.RetroSessionService;
import direct.reflect.facilitator.facilitation.session.RetroSyncVersionService;
import direct.reflect.facilitator.facilitation.session.RetroSessionNotFoundException;
import direct.reflect.facilitator.facilitation.dto.SyncVersionedResponse;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/retro")
@Tag(name = "Participant API", description = "Retrospective participant management")
@Slf4j
@RequiredArgsConstructor
public class ParticipantApiController {
    private final RetroSessionService retroService;
    private final ParticipantService participantService;
    private final RetroSyncVersionService retroSyncVersionService;

    @PostMapping(value = "/join", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('USER', 'GUEST')")
    public ResponseEntity<JoinRetroResponse> joinRetrospective(
            @Valid @RequestBody JoinRetroRequest request,
            HttpServletRequest httpRequest,
            Authentication authentication) {
        
        log.debug("=== JOIN REQUEST START ===");
        log.debug("Request retroId: {}", request.retroId());
        log.debug("Authentication: {} (type: {})", authentication.getName(), authentication.getClass().getSimpleName());
        
        UUID retroId;
        try {
            retroId = UUID.fromString(request.retroId());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid UUID format for retroId: {}", request.retroId());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        
        log.debug("Join request for retro: {} by user: {}", 
            retroId, authentication.getName());
        
        try {
            RetroSession sessionToJoin = retroService.getSessionById(retroId);
            participantService.addParticipantToSession(httpRequest, sessionToJoin, ParticipantRole.PARTICIPANT);
            
            log.debug("Successfully added participant to session: {}", retroId);
            
            return ResponseEntity.ok(new JoinRetroResponse(retroId, "/retro/" + retroId));
            
        } catch (RetroSessionNotFoundException e) {
            log.warn("Session not found: {}", retroId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (Exception e) {
            log.error("Error joining retro session {}: ", retroId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/leave-active-sessions")
    @PreAuthorize("hasAnyRole('USER', 'GUEST')")
    public ResponseEntity<LeaveActiveSessionsResult> leaveActiveSessions(HttpServletRequest httpRequest) {
        log.debug("Request to leave all active sessions");
        
        try {
            participantService.leaveAllActiveSessions(httpRequest);
            log.info("Successfully left all active sessions");
            return ResponseEntity.ok(new LeaveActiveSessionsResult(true));
        } catch (Exception e) {
            log.error("Error leaving active sessions: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @GetMapping("/check-active-sessions")
    @PreAuthorize("hasAnyRole('USER', 'GUEST')")
    public ResponseEntity<List<SessionInfo>> checkActiveSessions(HttpServletRequest httpRequest) {
        try {
            return ResponseEntity.ok(participantService.getActiveSessionInfos(httpRequest));
        } catch (Exception e) {
            log.error("Error checking active sessions: ", e);
            return ResponseEntity.ok(List.of());
        }
    }

    @GetMapping("/{retroId}/participants")
    @PreAuthorize("hasAnyRole('USER', 'GUEST')")
    public ResponseEntity<SyncVersionedResponse<List<ParticipantDto>>> getParticipants(
            @PathVariable UUID retroId,
            HttpServletRequest httpRequest) {

        try {
            participantService.getParticipantForSession(httpRequest, retroId);

            long syncVersion = retroSyncVersionService.getSyncVersion(retroId);
            List<ParticipantDto> participants = participantService.getSessionParticipants(retroId)
                .stream()
                .map(p -> new ParticipantDto(
                    p.getParticipantId(),
                    p.getDisplayName(),
                    p.getRole().name()
                ))
                .toList();

            return ResponseEntity.ok(new SyncVersionedResponse<>(syncVersion, participants));

        } catch (ParticipantNotFoundException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (Exception e) {
            log.error("Error getting participants for {}: ", retroId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
