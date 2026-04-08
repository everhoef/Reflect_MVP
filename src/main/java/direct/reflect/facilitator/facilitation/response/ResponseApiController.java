package direct.reflect.facilitator.facilitation.response;

import direct.reflect.facilitator.facilitation.dto.ColumnResponseDto;
import direct.reflect.facilitator.facilitation.dto.RatingResponseDto;
import direct.reflect.facilitator.facilitation.dto.SubmitResponseResult;
import direct.reflect.facilitator.facilitation.dto.VoteResult;
import direct.reflect.facilitator.facilitation.dto.RevealResult;
import direct.reflect.facilitator.facilitation.dto.UpdateResponseResult;
import direct.reflect.facilitator.facilitation.participant.Participant;
import direct.reflect.facilitator.facilitation.participant.ParticipantService;
import direct.reflect.facilitator.facilitation.participant.ParticipantNotFoundException;
import direct.reflect.facilitator.facilitation.session.RetroSession;
import direct.reflect.facilitator.facilitation.session.RetroSessionService;
import direct.reflect.facilitator.facilitation.session.RetroSessionNotFoundException;
import direct.reflect.facilitator.configurator.RetroStep;
import direct.reflect.facilitator.configurator.RetroStage;
import direct.reflect.facilitator.configurator.ComponentType;
import direct.reflect.facilitator.configurator.RetroStepQueryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "Response API", description = "Participant response management")
@Slf4j
@RequiredArgsConstructor
public class ResponseApiController {
    private final RetroSessionService retroService;
    private final ParticipantService participantService;
    private final ResponseService responseService;
    private final RetroStepQueryService retroStepQueryService;

    @PostMapping("/api/retros/{retroId}/steps/{stepId}/responses/column")
    @PreAuthorize("hasAnyRole('USER', 'GUEST')")
    @Operation(summary = "Submit a column/categorical response", description = "Submits a response to a multi-column board step")
    @ApiResponse(responseCode = "200", description = "Response submitted successfully")
    @ApiResponse(responseCode = "400", description = "Validation error or input limit exceeded")
    public ResponseEntity<SubmitResponseResult> submitColumnResponse(
            @PathVariable UUID retroId,
            @PathVariable Long stepId,
            @Valid @ModelAttribute ColumnResponseDto dto,
            HttpServletRequest httpRequest) {

        log.debug("Submitting column response for retro: {}, step: {}, column: {}",
            retroId, stepId, dto.columnId());

        try {
            ParticipantResponse saved = responseService.submitResponse(retroId, stepId, dto, httpRequest);
            log.debug("Submitted column response for step: {}", stepId);

            return ResponseEntity.ok()
                .header("HX-Trigger", "responseSubmitted")
                .body(new SubmitResponseResult(saved.getId(), stepId));

        } catch (InputLimitExceededException e) {
            log.debug("Input limit exceeded for retro {}: {}", retroId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (RetroSessionNotFoundException e) {
            log.debug("Session not found: {}", retroId);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error submitting column response: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/api/retros/{retroId}/steps/{stepId}/responses/rating")
    @PreAuthorize("hasAnyRole('USER', 'GUEST')")
    @Operation(summary = "Submit a rating response", description = "Submits a rating scale response for the current step")
    @ApiResponse(responseCode = "200", description = "Rating submitted successfully")
    @ApiResponse(responseCode = "400", description = "Validation error")
    public ResponseEntity<SubmitResponseResult> submitRatingResponse(
            @PathVariable UUID retroId,
            @PathVariable Long stepId,
            @Valid @ModelAttribute RatingResponseDto dto,
            HttpServletRequest httpRequest) {

        log.debug("Submitting rating response for retro: {}, step: {}, rating: {}",
            retroId, stepId, dto.rating());

        try {
            ParticipantResponse saved = responseService.submitResponse(retroId, stepId, dto, httpRequest);
            log.debug("Submitted rating response for step: {}", stepId);

            return ResponseEntity.ok()
                .header("HX-Trigger", "responseSubmitted")
                .body(new SubmitResponseResult(saved.getId(), stepId));

        } catch (RetroSessionNotFoundException e) {
            log.debug("Session not found: {}", retroId);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error submitting rating response: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/api/retros/{retroId}/steps/{stepId}/responses/rating/me")
    @PreAuthorize("hasAnyRole('USER', 'GUEST')")
    @Operation(summary = "Get current user's rating response",
        description = "Returns the authenticated user's rating response for a given step, or 404 if not yet submitted")
    @ApiResponse(responseCode = "200", description = "Rating response returned")
    @ApiResponse(responseCode = "404", description = "No rating response found for this participant and step")
    public ResponseEntity<RatingResponseDto> getMyRatingResponse(
            @PathVariable UUID retroId,
            @PathVariable Long stepId,
            HttpServletRequest httpRequest) {

        log.debug("Getting my rating response for retro: {}, step: {}", retroId, stepId);

        try {
            return responseService.getMyRatingResponse(retroId, stepId, httpRequest)
                .map(r -> ResponseEntity.ok(RatingResponseDto.from(r)))
                .orElse(ResponseEntity.notFound().build());

        } catch (ParticipantNotFoundException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (RetroSessionNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error fetching my rating response for retro {}, step {}: ", retroId, stepId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/api/retros/{retroId}/steps/{stepId}/responses/rating")
    @PreAuthorize("hasAnyRole('USER', 'GUEST')")
    @Operation(summary = "Get rating responses for histogram",
        description = "Returns all rating responses for the stage containing this step (used by HISTOGRAM_CHART component)")
    @ApiResponse(responseCode = "200", description = "Rating responses returned")
    @ApiResponse(responseCode = "403", description = "Participant is not part of this session")
    public ResponseEntity<List<RatingResponseDto>> getRatingResponses(
            @PathVariable UUID retroId,
            @PathVariable Long stepId,
            HttpServletRequest httpRequest) {

        try {
            if (!participantService.isParticipating(httpRequest, retroId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            RetroSession session = retroService.getSessionById(retroId);

            RetroStep step = retroStepQueryService.getStepById(stepId);
            RetroStage stage = step.getRetroStage();

            List<RatingResponseDto> dtos = responseService
                .getResponsesForStageComponentType(session, stage, ComponentType.RATING_SCALE)
                .stream()
                .map(RatingResponseDto::from)
                .toList();

            return ResponseEntity.ok(dtos);

        } catch (RetroSessionNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error fetching rating responses for retro {}, step {}: ", retroId, stepId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PutMapping("/api/retros/{retroId}/responses/{responseId}")
    @PreAuthorize("hasAnyRole('USER', 'GUEST')")
    @Operation(summary = "Update a response", description = "Updates the content of an existing participant response")
    @ApiResponse(responseCode = "200", description = "Response updated successfully")
    @ApiResponse(responseCode = "403", description = "Not authorized to edit this response")
    public ResponseEntity<UpdateResponseResult> updateResponse(
            @PathVariable UUID retroId,
            @PathVariable UUID responseId,
            @RequestParam String content,
            HttpServletRequest httpRequest) {

        log.debug("Updating response {} for retro: {}", responseId, retroId);

        try {
            Participant participant = participantService.getParticipantForSession(httpRequest, retroId);
            if (participant == null) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            ParticipantResponse saved = responseService.updateResponse(responseId, participant, content);

            log.debug("Updated response: {}", responseId);

            return ResponseEntity.ok(new UpdateResponseResult(saved.getId(), content));

        } catch (SecurityException e) {
            log.warn("Unauthorized response update attempt: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (Exception e) {
            log.error("Error updating response: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/api/retros/{retroId}/responses/{responseId}/vote")
    @PreAuthorize("hasAnyRole('USER', 'GUEST')")
    @Operation(summary = "Toggle a vote on a response",
        description = "Adds or removes a vote for the current participant on the specified response")
    @ApiResponse(responseCode = "200", description = "Vote toggled successfully, returns updated vote count")
    @ApiResponse(responseCode = "400", description = "Vote limit exceeded")
    public ResponseEntity<VoteResult> toggleVote(
            @PathVariable UUID retroId,
            @PathVariable UUID responseId,
            HttpServletRequest httpRequest) {

        log.debug("Toggling vote for response {} in retro: {}", responseId, retroId);

        try {
            ParticipantResponse saved = responseService.toggleVote(retroId, responseId, httpRequest);
            log.debug("Toggled vote for response: {}", responseId);

            Object votesObj = saved.getResponseData().get("votes");
            int voteCount = 0;
            if (votesObj instanceof List<?>) {
                voteCount = ((List<?>) votesObj).size();
            }

            return ResponseEntity.ok()
                .header("HX-Trigger", "voteToggled")
                .body(new VoteResult(responseId, voteCount));

        } catch (VoteLimitExceededException e) {
            log.debug("Vote limit exceeded: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (SecurityException e) {
            log.debug("Unauthorized vote attempt: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (Exception e) {
            log.error("Error toggling vote: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping("/api/retros/{retroId}/responses/{responseId}")
    @PreAuthorize("hasAnyRole('USER', 'GUEST')")
    @Operation(summary = "Delete a response", description = "Deletes a participant's own response (used by ESVP_SELECTOR for re-selection)")
    @ApiResponse(responseCode = "204", description = "Response deleted successfully")
    @ApiResponse(responseCode = "403", description = "Not authorized to delete this response")
    public ResponseEntity<Void> deleteResponse(
            @PathVariable UUID retroId,
            @PathVariable UUID responseId,
            HttpServletRequest httpRequest) {

        log.debug("Deleting response {} for retro: {}", responseId, retroId);

        try {
            Participant participant = participantService.getParticipantForSession(httpRequest, retroId);
            if (participant == null) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            responseService.deleteResponse(responseId, participant);
            log.debug("Deleted response: {}", responseId);

            return ResponseEntity.noContent().build();

        } catch (SecurityException e) {
            log.warn("Unauthorized response delete attempt: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (Exception e) {
            log.error("Error deleting response: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/api/retros/{retroId}/steps/{stepId}/responses/reveal")
    @PreAuthorize("hasAnyRole('USER', 'GUEST')")
    @Operation(summary = "Reveal responses for a step", description = "Makes all participant responses visible; facilitator-only action")
    @ApiResponse(responseCode = "200", description = "Responses revealed successfully")
    @ApiResponse(responseCode = "403", description = "Only facilitators can reveal responses")
    public ResponseEntity<RevealResult> revealResponses(
            @PathVariable UUID retroId,
            @PathVariable Long stepId,
            HttpServletRequest httpRequest) {

        log.debug("Revealing responses for retro: {}, step: {}", retroId, stepId);

        try {
            boolean isFacilitator = participantService.isFacilitator(httpRequest, retroId);
            if (!isFacilitator) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            RetroSession session = retroService.getSessionById(retroId);
            if (session == null) {
                return ResponseEntity.notFound().build();
            }

            responseService.revealAllResponses(session, stepId);

            log.debug("Revealed responses for step: {} in retro: {}", stepId, retroId);

            return ResponseEntity.ok()
                .header("HX-Trigger", "responsesRevealed")
                .body(new RevealResult(stepId, true));

        } catch (Exception e) {
            log.error("Error revealing responses: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
