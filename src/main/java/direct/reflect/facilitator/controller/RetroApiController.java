package direct.reflect.facilitator.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import direct.reflect.facilitator.domain.entity.RetroSession;
import direct.reflect.facilitator.service.ParticipantService;
import direct.reflect.facilitator.service.RetroSessionService;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

@RestController
@RequestMapping("/api/retro")
@RequiredArgsConstructor
public class RetroApiController {
    private final RetroSessionService retroService;
    private final ParticipantService participantService;

    @PostMapping("/create")
    public ResponseEntity<RetroSession> createRetrospective(
            @RequestParam String sessionName,
            @RequestParam Long templateId) {
        RetroSession retro = retroService.createNewSession(sessionName, templateId);
        participantService.addFacilitator(retro.getRetroId());
        return ResponseEntity
            .ok()
            .header("HX-Redirect", "/retrospective/" + retro.getRetroId())
            .body(retro);
    }

    @PostMapping("/{retroId}/join")
    public ResponseEntity<?> joinRetrospective(
            @PathVariable UUID retroId,
            @RequestParam String nickname) {
        if (!retroService.sessionExists(retroId)) {
            return ResponseEntity.notFound()
                .header("HX-Redirect", "/?error=session_not_found")
                .build();
        }
        participantService.addParticipant(retroId, nickname);
        return ResponseEntity.ok()
            .header("HX-Redirect", "/retrospective/" + retroId)
            .build();
    }

    @PostMapping("/{retroId}/start")
    public ResponseEntity<?> startSession(@PathVariable UUID retroId) {
        if (!participantService.isFacilitator(retroId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .header("HX-Redirect", "/retrospective/" + retroId)
                .build();
        }
        retroService.startSession(retroId);
        return ResponseEntity.ok()
            .header("HX-Redirect", "/retrospective/" + retroId + "/session")
            .build();
    }

    @PostMapping("/{retroId}/next")
    public ResponseEntity<?> nextStep(@PathVariable UUID retroId) {
        if (!participantService.isFacilitator(retroId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .header("HX-Redirect", "/retrospective/" + retroId + "/session")
                .build();
        }
        retroService.advanceToNextStep(retroId);
        return ResponseEntity.ok()
            .header("HX-Trigger", "stepAdvanced")
            .build();
    }
}
