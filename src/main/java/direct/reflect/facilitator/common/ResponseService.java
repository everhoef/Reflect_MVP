package direct.reflect.facilitator.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * Service responsible for standardizing HTTP response creation across controllers.
 * Follows GRASP principles by centralizing response formatting logic.
 */
@Service
@Slf4j
public class ResponseService {
    
    /**
     * Creates a redirect response with HX-Redirect header for HTMX requests.
     * 
     * @param redirectUrl the URL to redirect to
     * @return ResponseEntity with redirect
     */
    public ResponseEntity<Void> createRedirectResponse(String redirectUrl) {
        log.debug("Creating redirect response to: {}", redirectUrl);
        return ResponseEntity.status(HttpStatus.FOUND)
            .header("HX-Redirect", redirectUrl)
            .build();
    }
    
    /**
     * Creates a redirect response for errors with error parameter.
     * 
     * @param baseUrl the base URL to redirect to
     * @param errorCode the error code to append
     * @return ResponseEntity with error redirect
     */
    public ResponseEntity<Void> createErrorRedirectResponse(String baseUrl, String errorCode) {
        String redirectUrl = baseUrl + "?error=" + errorCode;
        log.debug("Creating error redirect response to: {}", redirectUrl);
        return ResponseEntity.status(HttpStatus.SEE_OTHER)
            .header("HX-Redirect", redirectUrl)
            .build();
    }
    
    /**
     * Creates a bad request response with error redirect.
     * 
     * @param baseUrl the base URL to redirect to
     * @param errorCode the error code to append
     * @return ResponseEntity with bad request status
     */
    public ResponseEntity<Void> createBadRequestResponse(String baseUrl, String errorCode) {
        String redirectUrl = baseUrl + "?error=" + errorCode;
        log.debug("Creating bad request response with redirect to: {}", redirectUrl);
        return ResponseEntity.badRequest()
            .header("HX-Redirect", redirectUrl)
            .build();
    }
    
    /**
     * Creates an internal server error response with error redirect.
     * 
     * @param baseUrl the base URL to redirect to
     * @param errorCode the error code to append
     * @return ResponseEntity with internal server error status
     */
    public ResponseEntity<Void> createServerErrorResponse(String baseUrl, String errorCode) {
        String redirectUrl = baseUrl + "?error=" + errorCode;
        log.debug("Creating server error response with redirect to: {}", redirectUrl);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .header("HX-Redirect", redirectUrl)
            .build();
    }
    
    /**
     * Creates a forbidden response with error redirect.
     * 
     * @param baseUrl the base URL to redirect to  
     * @return ResponseEntity with forbidden status
     */
    public ResponseEntity<Void> createForbiddenResponse(String baseUrl) {
        log.debug("Creating forbidden response with redirect to: {}", baseUrl);
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .header("HX-Redirect", baseUrl)
            .build();
    }
    
    /**
     * Creates a not found response with error redirect.
     * 
     * @param baseUrl the base URL to redirect to
     * @param errorCode the error code to append
     * @return ResponseEntity with not found status
     */
    public ResponseEntity<Void> createNotFoundResponse(String baseUrl, String errorCode) {
        String redirectUrl = baseUrl + "?error=" + errorCode;
        log.debug("Creating not found response with redirect to: {}", redirectUrl);
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .header("HX-Redirect", redirectUrl)
            .build();
    }
    
    /**
     * Creates a success response with custom header.
     * 
     * @param headerName the header name
     * @param headerValue the header value  
     * @return ResponseEntity with OK status
     */
    public ResponseEntity<Void> createSuccessResponseWithHeader(String headerName, String headerValue) {
        log.debug("Creating success response with header {}={}", headerName, headerValue);
        return ResponseEntity.ok()
            .header(headerName, headerValue)
            .build();
    }
    
    /**
     * Maps exceptions to appropriate error codes for session creation operations.
     * 
     * @param exception the exception to map
     * @return error code string
     */
    public String mapSessionCreationException(Throwable exception) {
        if (exception instanceof IllegalArgumentException) {
            if (exception.getMessage().contains("display name")) {
                return "missing_display_name";
            }
            return "creation_failed";
        } else if (exception instanceof IllegalStateException) {
            return "active_session_exists";
        }
        return "creation_failed";
    }
    
    /**
     * Maps exceptions to appropriate error codes for session joining operations.
     * 
     * @param exception the exception to map
     * @return error code string
     */
    public String mapSessionJoinException(Throwable exception) {
        if (exception instanceof IllegalArgumentException) {
            if (exception.getMessage().contains("display name")) {
                return "missing_display_name";
            }
            return "join_failed";
        } else if (exception instanceof IllegalStateException) {
            return "active_session_exists";
        }
        return "join_failed";
    }
    
    /**
     * Maps exceptions to appropriate error codes for session operations.
     * 
     * @param exception the exception to map
     * @param operation the operation being performed ("start", "advance", etc.)
     * @return error code string
     */
    public String mapSessionOperationException(Throwable exception, String operation) {
        return operation + "_failed";
    }
}