package direct.reflect.facilitator.common;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

/**
 * Service responsible for request validation logic.
 * Follows GRASP principles by centralizing validation concerns.
 */
@Service
@Slf4j
public class RequestValidationService {
    
    /**
     * Validates that a session name is provided and not blank.
     * 
     * @param sessionName the session name to validate
     * @return validation result
     */
    public ValidationResult validateSessionName(String sessionName) {
        if (!StringUtils.hasText(sessionName)) {
            log.debug("Session name validation failed: name is missing or blank");
            return ValidationResult.invalid("missing_session_name", "Session name is required");
        }
        return ValidationResult.valid();
    }
    
    /**
     * Validates that a retro ID is provided and not null.
     * 
     * @param retroId the retro ID to validate
     * @return validation result
     */
    public ValidationResult validateRetroId(UUID retroId) {
        if (retroId == null) {
            log.debug("Retro ID validation failed: ID is null");
            return ValidationResult.invalid("missing_retro_id", "Retro ID is required");
        }
        return ValidationResult.valid();
    }
    
    /**
     * Validates display name for guest authentication.
     * 
     * @param displayName the display name to validate
     * @return validation result
     */
    public ValidationResult validateDisplayName(String displayName) {
        if (!StringUtils.hasText(displayName)) {
            log.debug("Display name validation failed: name is missing or blank");
            return ValidationResult.invalid("missing_display_name", "Display name is required");
        }
        return ValidationResult.valid();
    }
    
    /**
     * Validates user credentials for authentication.
     * 
     * @param username the username to validate
     * @param password the password to validate
     * @return validation result
     */
    public ValidationResult validateUserCredentials(String username, String password) {
        if (!StringUtils.hasText(username)) {
            log.debug("User credentials validation failed: username is missing");
            return ValidationResult.invalid("missing_credentials", "Username is required");
        }
        if (!StringUtils.hasText(password)) {
            log.debug("User credentials validation failed: password is missing");
            return ValidationResult.invalid("missing_credentials", "Password is required");
        }
        return ValidationResult.valid();
    }
    
    /**
     * Validates login type parameter.
     * 
     * @param loginType the login type to validate
     * @return validation result
     */
    public ValidationResult validateLoginType(String loginType) {
        if (!StringUtils.hasText(loginType)) {
            log.debug("Login type validation failed: type is missing or blank");
            return ValidationResult.invalid("missing_login_type", "Login type is required");
        }
        
        String normalizedType = loginType.toLowerCase().trim();
        if (!"guest".equals(normalizedType) && !"user".equals(normalizedType)) {
            log.debug("Login type validation failed: invalid type '{}'", loginType);
            return ValidationResult.invalid("invalid_login_type", "Login type must be 'guest' or 'user'");
        }
        
        return ValidationResult.valid();
    }
    
    /**
     * Represents the result of a validation operation.
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String errorCode;
        private final String errorMessage;
        
        private ValidationResult(boolean valid, String errorCode, String errorMessage) {
            this.valid = valid;
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
        }
        
        public static ValidationResult valid() {
            return new ValidationResult(true, null, null);
        }
        
        public static ValidationResult invalid(String errorCode, String errorMessage) {
            return new ValidationResult(false, errorCode, errorMessage);
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public String getErrorCode() {
            return errorCode;
        }
        
        public String getErrorMessage() {
            return errorMessage;
        }
    }
}