package direct.reflect.facilitator.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

import direct.reflect.facilitator.common.exception.NotAuthenticatedException;
import direct.reflect.facilitator.common.exception.ParticipantNotFoundException;
import direct.reflect.facilitator.common.exception.RetroSessionNotFoundException;
import direct.reflect.facilitator.common.exception.RetroTemplateNotFoundException;
import lombok.extern.slf4j.Slf4j;

/**
 * Exception handler for view controllers.
 * Handles custom exceptions and provides appropriate user-friendly redirects.
 */
@ControllerAdvice(annotations = Controller.class)
@Slf4j
public class ViewExceptionHandler {

    @ExceptionHandler(RetroSessionNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleRetroSessionNotFound(RetroSessionNotFoundException ex) {
        log.error("Retro session not found: {}", ex.getMessage());
        return "redirect:/?error=session_not_found";
    }

    @ExceptionHandler(ParticipantNotFoundException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String handleParticipantNotFound(ParticipantNotFoundException ex) {
        log.error("Participant not found: {}", ex.getMessage());
        return "redirect:/?error=participant_not_found";
    }

    @ExceptionHandler(RetroTemplateNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleRetroTemplateNotFound(RetroTemplateNotFoundException ex) {
        log.error("Retro template not found: {}", ex.getMessage());
        return "redirect:/?error=template_not_found";
    }

    @ExceptionHandler(NotAuthenticatedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public String handleNotAuthenticated(NotAuthenticatedException ex) {
        log.error("Authentication required: {}", ex.getMessage());
        return "redirect:/login?error=authentication_required";
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleGenericException(Exception ex) {
        log.error("Unexpected error occurred: ", ex);
        return "redirect:/?error=internal_error";
    }
}
