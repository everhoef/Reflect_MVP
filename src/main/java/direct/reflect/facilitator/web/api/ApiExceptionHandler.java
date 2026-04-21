package direct.reflect.facilitator.web.api;

import direct.reflect.facilitator.configurator.RetroTemplateNotFoundException;
import direct.reflect.facilitator.facilitation.actions.ActionItemNotFoundException;
import direct.reflect.facilitator.facilitation.clustering.ClusterNotFoundException;
import direct.reflect.facilitator.facilitation.clustering.ClusterResponseNotFoundException;
import direct.reflect.facilitator.facilitation.escalation.EscalatedItemNotFoundException;
import direct.reflect.facilitator.facilitation.participant.ParticipantNotFoundException;
import direct.reflect.facilitator.facilitation.response.VoteLimitExceededException;
import direct.reflect.facilitator.facilitation.session.InvalidSessionStateException;
import direct.reflect.facilitator.facilitation.session.InvalidStepException;
import direct.reflect.facilitator.facilitation.session.RetroSessionNotFoundException;
import direct.reflect.facilitator.organization.DuplicateOrganizationSlugException;
import direct.reflect.facilitator.organization.OrganizationNotFoundException;
import direct.reflect.facilitator.organization.TeamNotFoundException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.core.annotation.Order;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.exc.InvalidFormatException;
import lombok.extern.slf4j.Slf4j;

/**
 * Exception handler for API controllers.
 * Handles custom exceptions and provides appropriate HTTP status codes.
 */
@RestControllerAdvice
@Order(1)
@Slf4j
public class ApiExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<String> handleResponseStatusException(ResponseStatusException ex) {
        log.error("ResponseStatusException: {}", ex.getMessage());
        return ResponseEntity.status(ex.getStatusCode())
            .body("Request failed");
    }

    @ExceptionHandler({
        OrganizationNotFoundException.class,
        TeamNotFoundException.class,
        ActionItemNotFoundException.class,
        ClusterNotFoundException.class,
        ClusterResponseNotFoundException.class,
        EscalatedItemNotFoundException.class
    })
    public ResponseEntity<String> handleResourceNotFound(RuntimeException ex) {
        log.error("Resource not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body("Resource not found");
    }

    @ExceptionHandler(RetroSessionNotFoundException.class)
    public ResponseEntity<String> handleRetroSessionNotFound(RetroSessionNotFoundException ex) {
        log.error("Retro session not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body("Session not found");
    }

    @ExceptionHandler(ParticipantNotFoundException.class)
    public ResponseEntity<String> handleParticipantNotFound(ParticipantNotFoundException ex) {
        log.error("Participant not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body("Participant not found");
    }

    @ExceptionHandler(RetroTemplateNotFoundException.class)
    public ResponseEntity<String> handleRetroTemplateNotFound(RetroTemplateNotFoundException ex) {
        log.error("Retro template not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body("Template not found");
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<String> handleAuthorizationDenied(AuthorizationDeniedException ex) {
        log.error("Access denied: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .contentType(MediaType.APPLICATION_JSON)
            .body("{\"error\":\"Access denied\"}");
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<String> handleValidationException(Exception ex) {
        String errors;

        if (ex instanceof MethodArgumentNotValidException validEx) {
            errors = validEx.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getDefaultMessage())
                .collect(java.util.stream.Collectors.joining("; "));
        } else if (ex instanceof BindException bindEx) {
            errors = bindEx.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getDefaultMessage())
                .collect(java.util.stream.Collectors.joining("; "));
        } else {
            errors = "Invalid input provided";
        }

        log.warn("Input validation failed: {}", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body("Validation failed: " + errors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<String> handleJsonDeserialization(HttpMessageNotReadableException ex) {
        log.warn("JSON deserialization failed: {}", ex.getMessage());

        // Check if this is a UUID format error specifically
        if (ex.getCause() instanceof InvalidFormatException) {
            InvalidFormatException ife = (InvalidFormatException) ex.getCause();
            if (ife.getTargetType().equals(java.util.UUID.class)) {
                log.warn("UUID format error for value: {}", ife.getValue());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .header("HX-Redirect", "/home?error=invalid_input")
                    .body("Invalid session ID format");
            }
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .header("HX-Redirect", "/home?error=invalid_input")
            .body("Invalid request format");
    }

    @ExceptionHandler(InvalidSessionStateException.class)
    public ResponseEntity<String> handleInvalidSessionState(InvalidSessionStateException ex) {
        log.warn("Invalid session state: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ex.getMessage());
    }

    @ExceptionHandler(InvalidStepException.class)
    public ResponseEntity<String> handleInvalidStep(InvalidStepException ex) {
        log.warn("Invalid step: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ex.getMessage());
    }

    @ExceptionHandler(VoteLimitExceededException.class)
    public ResponseEntity<String> handleVoteLimitExceeded(VoteLimitExceededException ex) {
        log.warn("Vote limit exceeded: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ex.getMessage());
    }

    @ExceptionHandler(DuplicateOrganizationSlugException.class)
    public ResponseEntity<String> handleDuplicateOrganizationSlug(DuplicateOrganizationSlugException ex) {
        log.warn("Duplicate organization slug: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Invalid request argument: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body("Invalid request");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleGenericException(Exception ex) {
        log.error("Unexpected error occurred: ", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body("Internal server error");
    }
}
