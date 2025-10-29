package direct.reflect.facilitator.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import direct.reflect.facilitator.common.exception.NotAuthenticatedException;
import direct.reflect.facilitator.common.exception.ParticipantNotFoundException;
import direct.reflect.facilitator.common.exception.RetroSessionNotFoundException;
import direct.reflect.facilitator.common.exception.RetroTemplateNotFoundException;
import lombok.extern.slf4j.Slf4j;

/**
 * Exception handler for view controllers.
 * Handles custom exceptions and provides appropriate responses for both full-page and HTMX requests.
 */
@ControllerAdvice(annotations = Controller.class)
@Slf4j
public class ViewExceptionHandler {

    /**
     * Check if the request is from HTMX by looking for the HX-Request header.
     */
    private boolean isHtmxRequest(HttpServletRequest request) {
        return "true".equals(request.getHeader("HX-Request"));
    }

    @ExceptionHandler(RetroSessionNotFoundException.class)
    public String handleRetroSessionNotFound(RetroSessionNotFoundException ex, HttpServletRequest request, Model model) {
        log.error("Retro session not found: {}", ex.getMessage());

        if (isHtmxRequest(request)) {
            model.addAttribute("errorMessage", "Session not found");
            return "fragments/error :: htmx-error";
        }

        return "redirect:/?error=session_not_found";
    }

    @ExceptionHandler(ParticipantNotFoundException.class)
    public String handleParticipantNotFound(ParticipantNotFoundException ex, HttpServletRequest request, Model model) {
        log.error("Participant not found: {}", ex.getMessage());

        if (isHtmxRequest(request)) {
            model.addAttribute("errorMessage", "Access denied");
            return "fragments/error :: htmx-error";
        }

        return "redirect:/?error=participant_not_found";
    }

    @ExceptionHandler(RetroTemplateNotFoundException.class)
    public String handleRetroTemplateNotFound(RetroTemplateNotFoundException ex, HttpServletRequest request, Model model) {
        log.error("Retro template not found: {}", ex.getMessage());

        if (isHtmxRequest(request)) {
            model.addAttribute("errorMessage", "Template not found");
            return "fragments/error :: htmx-error";
        }

        return "redirect:/?error=template_not_found";
    }

    @ExceptionHandler(NotAuthenticatedException.class)
    public String handleNotAuthenticated(NotAuthenticatedException ex, HttpServletRequest request, Model model) {
        log.error("Authentication required: {}", ex.getMessage());

        if (isHtmxRequest(request)) {
            model.addAttribute("errorMessage", "Authentication required");
            return "fragments/error :: htmx-error";
        }

        return "redirect:/login?error=authentication_required";
    }

    @ExceptionHandler(Exception.class)
    public String handleGenericException(Exception ex, HttpServletRequest request, Model model) {
        log.error("Unexpected error occurred: ", ex);

        if (isHtmxRequest(request)) {
            model.addAttribute("errorMessage", "An error occurred");
            return "fragments/error :: htmx-error";
        }

        return "redirect:/?error=internal_error";
    }
}
