package direct.reflect.facilitator.common.exception;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import direct.reflect.facilitator.common.exception.NotAuthenticatedException;
import direct.reflect.facilitator.common.exception.ParticipantNotFoundException;
import direct.reflect.facilitator.common.exception.RetroSessionNotFoundException;
import direct.reflect.facilitator.common.exception.RetroTemplateNotFoundException;
import lombok.extern.slf4j.Slf4j;

/**
 * Exception handler for API controllers.
 * Handles custom exceptions and provides appropriate HTTP status codes.
 */
@ControllerAdvice(basePackages = {"direct.reflect.facilitator.eventing", "direct.reflect.facilitator.facilitation"})
@Order(1)
@Slf4j
public class ApiExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<String> handleResponseStatusException(ResponseStatusException ex) {
        log.error("ResponseStatusException: {}", ex.getMessage());
        return ResponseEntity.status(ex.getStatusCode())
            .body("Request failed");
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

    @ExceptionHandler(NotAuthenticatedException.class)
    public ResponseEntity<String> handleNotAuthenticated(NotAuthenticatedException ex) {
        log.error("Authentication required: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body("Authentication required");
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<String> handleAuthorizationDenied(AuthorizationDeniedException ex) {
        log.error("Access denied: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body("{\"error\":\"Authentication required\",\"loginUrl\":\"/login\"}");
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<String> handleValidationException(Exception ex) {
        log.warn("Input validation failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body("Invalid input provided");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleGenericException(Exception ex) {
        log.error("Unexpected error occurred: ", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body("Internal server error");
    }
}
