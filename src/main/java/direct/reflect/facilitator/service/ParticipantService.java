package direct.reflect.facilitator.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.http.ResponseCookie;
import org.springframework.http.HttpCookie;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import direct.reflect.facilitator.domain.entity.Participant;
import direct.reflect.facilitator.domain.entity.RetroSession;
import direct.reflect.facilitator.domain.enums.ParticipantRole;
import direct.reflect.facilitator.repository.ParticipantRepository;
import direct.reflect.facilitator.repository.RetroSessionRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class ParticipantService {
    private final ParticipantRepository participantRepository;
    private final RetroSessionRepository retroSessionRepository;
    
    private static final String PARTICIPANT_ID_COOKIE = "PARTICIPANT_ID";
    private static final int COOKIE_MAX_AGE = 60 * 60 * 24 * 30; // 30 days
    
    /**
     * Get or create a participant from the current web context
     */
    public Mono<Participant> getCurrentParticipant(ServerWebExchange exchange, String displayName) {
        return getOrGenerateParticipantId(exchange)
            .flatMap(participantId -> 
                getAuthenticatedUsername()
                    .map(username -> {
                        // For authenticated users:
                        // - participantId = persistent cookie-based ID 
                        // - username = authentication username
                        // - displayName = can be customized or default to username
                        return findOrCreateParticipant(participantId, username, displayName);
                    })
                    .defaultIfEmpty(
                        // For anonymous users:
                        // - participantId = persistent cookie-based ID
                        // - username = null
                        // - displayName = user-provided display name
                        findOrCreateParticipant(participantId, null, displayName)
                    )
            );
    }
    
    /**
     * Add current user as facilitator
     */
    public Mono<Participant> addFacilitator(ServerWebExchange exchange, UUID retroId, String displayName) {
        return getCurrentParticipant(exchange, displayName)
            .map(participant -> {
                RetroSession session = retroSessionRepository.findByRetroId(retroId)
                    .orElseThrow(() -> new IllegalStateException("Retro session not found"));
                
                participant.setRole(ParticipantRole.FACILITATOR);
                participant.setSession(session);
                participant.setLastSeen(LocalDateTime.now());
                return participantRepository.save(participant);
            });
    }
    
    /**
     * Add current user as participant
     */
    public Mono<Participant> addParticipant(ServerWebExchange exchange, UUID retroId, String displayName) {
        return getCurrentParticipant(exchange, displayName)
            .map(participant -> {
                RetroSession session = retroSessionRepository.findByRetroId(retroId)
                    .orElseThrow(() -> new IllegalStateException("Retro session not found"));
                
                participant.setSession(session);
                participant.setLastSeen(LocalDateTime.now());
                return participantRepository.save(participant);
            });
    }
    
    /**
     * Get all participants for a retro session
     */
    public List<Participant> getSessionParticipants(UUID retroId) {
        return participantRepository.findBySession_RetroId(retroId);
    }
    
    /**
     * Check if current user is a facilitator
     */
    public Mono<Boolean> isFacilitator(ServerWebExchange exchange, UUID retroId) {
        return getCurrentParticipant(exchange, null)
            .map(participant -> 
                participantRepository.findBySession_RetroIdAndRoleAndParticipantId(
                    retroId, ParticipantRole.FACILITATOR, participant.getParticipantId()
                ).isPresent()
            )
            .defaultIfEmpty(false);
    }
    
    /**
     * Check if current user is participating in a session
     */
    public Mono<Boolean> isParticipating(ServerWebExchange exchange, UUID retroId) {
        return getCurrentParticipant(exchange, null)
            .map(participant -> 
                participantRepository.existsBySession_RetroIdAndParticipantId(
                    retroId, participant.getParticipantId()
                )
            )
            .defaultIfEmpty(false);
    }
    
    /**
     * Update last seen timestamp for current user
     */
    public Mono<Void> updateLastSeen(ServerWebExchange exchange) {
        return getCurrentParticipant(exchange, null)
            .map(participant -> {
                participant.setLastSeen(LocalDateTime.now());
                participantRepository.save(participant);
                return participant;
            })
            .then();
    }
    
    /**
     * Look up or create a participant by ID
     */
    private Participant findOrCreateParticipant(String participantId, String username, String displayName) {
        // Try to find by participant ID first - this is our primary lookup
        Optional<Participant> existingById = participantRepository.findByParticipantId(participantId);
        if (existingById.isPresent()) {
            Participant participant = existingById.get();
            
            // Update if needed
            boolean needsUpdate = false;
            
            // If user has authenticated, update their username
            if (username != null && !username.equals(participant.getUsername())) {
                participant.setUsername(username);
                needsUpdate = true;
            }
            
            // If display name provided, update it
            if (displayName != null && !displayName.isBlank() && !displayName.equals(participant.getDisplayName())) {
                participant.setDisplayName(displayName);
                needsUpdate = true;
            }
            
            if (needsUpdate) {
                return participantRepository.save(participant);
            }
            
            return participant;
        }
        
        // If authenticated user, try to find by username (for existing users)
        if (username != null && !username.isBlank()) {
            Optional<Participant> existingByUsername = participantRepository.findByUsername(username);
            if (existingByUsername.isPresent()) {
                Participant participant = existingByUsername.get();
                // Link their cookie ID to their authenticated account
                participant.setParticipantId(participantId);
                
                if (displayName != null && !displayName.isBlank()) {
                    participant.setDisplayName(displayName);
                }
                
                return participantRepository.save(participant);
            }
        }
        
        // Create new participant with all three fields
        Participant newParticipant = new Participant();
        newParticipant.setParticipantId(participantId);          // Always set (cookie-based UUID)
        newParticipant.setUsername(username);                    // Only set for authenticated users
        
        // Set display name with priority:
        // 1. User-provided display name
        // 2. Username (if authenticated)
        // 3. "Anonymous" fallback
        if (displayName != null && !displayName.isBlank()) {
            newParticipant.setDisplayName(displayName);
        } else if (username != null) {
            newParticipant.setDisplayName(username);
        } else {
            newParticipant.setDisplayName("Anonymous");
        }
        
        newParticipant.setLastSeen(LocalDateTime.now());
        return participantRepository.save(newParticipant);
    }
    
    /**
     * Get authenticated username from security context
     */
    private Mono<String> getAuthenticatedUsername() {
        return ReactiveSecurityContextHolder.getContext()
            .map(SecurityContext::getAuthentication)
            .filter(auth -> auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName()))
            .map(Authentication::getName);
    }
    
    /**
     * Get or generate participant ID from cookie
     */
    private Mono<String> getOrGenerateParticipantId(ServerWebExchange exchange) {
        String participantId = getParticipantIdFromCookie(exchange);
        
        if (participantId != null && !participantId.isBlank()) {
            return Mono.just(participantId);
        }
        
        // Generate new ID and set cookie
        String newParticipantId = UUID.randomUUID().toString();
        setParticipantIdCookie(exchange, newParticipantId);
        return Mono.just(newParticipantId);
    }
    
    /**
     * Get participant ID from cookie
     */
    private String getParticipantIdFromCookie(ServerWebExchange exchange) {
        HttpCookie cookie = exchange.getRequest().getCookies().getFirst(PARTICIPANT_ID_COOKIE);
        return cookie != null ? cookie.getValue() : null;
    }
    
    /**
     * Set participant ID cookie
     */
    private void setParticipantIdCookie(ServerWebExchange exchange, String participantId) {
        ResponseCookie cookie = ResponseCookie.from(PARTICIPANT_ID_COOKIE, participantId)
            .maxAge(COOKIE_MAX_AGE)
            .httpOnly(true)  // Not accessible via JavaScript
            .path("/")       // Available across the site
            .build();
            
        exchange.getResponse().addCookie(cookie);
    }
}
