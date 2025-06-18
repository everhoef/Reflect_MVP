package direct.reflect.facilitator.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.Optional;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.http.ResponseCookie;
import org.springframework.http.HttpCookie;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import direct.reflect.facilitator.domain.entity.Participant;
import direct.reflect.facilitator.domain.entity.ParticipantId;
import direct.reflect.facilitator.domain.entity.RetroSession;
import direct.reflect.facilitator.domain.enums.ParticipantRole;
import direct.reflect.facilitator.repository.ParticipantRepository;

/**
 * Service responsible for managing {@link Participant} entities.
 * This includes creating participants, assigning roles (like FACILITATOR),
 * associating them with {@link RetroSession}s, and handling participant identification
 * through cookies for anonymous users and Spring Security for authenticated users.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ParticipantService {
    private final ParticipantRepository participantRepository;
    private final RetroSessionService retroSessionService;

    private static final TimeBasedEpochGenerator UUID_V7_EPOCH_GENERATOR = Generators.timeBasedEpochGenerator();

    public static final String PARTICIPANT_ID_COOKIE = "PARTICIPANT_ID"; // Made public for test access
    public static final int COOKIE_MAX_AGE = 60 * 60 * 24 * 30; // Made public for test access

    /**
     * Creates a new {@link RetroSession} via {@link RetroSessionService},
     * then creates a new {@link Participant} for this session, associating them with a stable user identifier (participantId),
     * sets their role to FACILITATOR, and saves the participant.
     *
     * This method ensures a participant (identified by their stable {@code participantId})
     * can only be in one active (non-terminal phase) session at a time.
     * If the participant is already in another active session, an {@link IllegalStateException} is thrown.
     *
     * This method orchestrates the creation of a session and its initial facilitator by:
     * <ol>
     *   <li>Calls {@code retroSessionService.createNewSession} to create the new {@link RetroSession} entity.</li>
     *   <li>Determines the current user's stable {@code participantId} (a {@link UUID}) by checking for the {@link #PARTICIPANT_ID_COOKIE} or generating a new one if not found or invalid. This ID is used to link the user across multiple sessions.</li>
     *   <li>Checks if the participant, identified by the stable {@code participantId}, is already part of another active (non-terminal phase) session by querying existing participations. If so, an {@link IllegalStateException} is thrown.</li>
     *   <li>Retrieves the username of the currently authenticated principal, if any, using Spring Security context.</li>
     *   <li>Creates a new {@link Participant} entity for the newly created session.</li>
     *   <li>Populates this new {@code Participant} with:
     *     <ul>
     *       <li>The stable {@code participantId}.</li>
     *       <li>A reference to the new {@code RetroSession}.</li>
     *       <li>The provided {@code displayName}.</li>
     *       <li>The role set to {@link ParticipantRole#FACILITATOR}.</li>
     *       <li>The current {@link LocalDateTime} as {@code lastSeen}.</li>
     *       <li>The username, if an authenticated user is present. If not, it attempts to find a username from previous participations associated with the same stable {@code participantId}.</li>
     *     </ul>
     *   </li>
     *   <li>Persists the newly configured {@link Participant} to the database.</li>
     *   <li>If a new {@code participantId} was generated, the {@link #PARTICIPANT_ID_COOKIE} is set in the HTTP response.</li>
     * </ol>
     *
     * @param sessionName The name for the new retro session.
     * @param displayName The display name for the facilitator.
     * @param exchange The current {@link ServerWebExchange} for cookie handling and security context.
     * @return A {@link Mono} emitting the created and persisted {@link Participant} who is the facilitator of the new session.
     */
    @Transactional
    public Mono<Participant> createAndAssignFacilitatorForSession(String sessionName, String displayName, ServerWebExchange exchange) {
        RetroSession newSession = retroSessionService.createNewSession(sessionName);
        return getOrGenerateParticipantId(exchange)
            .flatMap(participantUuid -> {
                // Enforce only one active session per participant
                List<Participant> existing = participantRepository.findByParticipantId(participantUuid);
                if (!existing.isEmpty()) {
                    // Check if any session is still active (not completed/abandoned)
                    boolean hasActive = existing.stream().anyMatch(p -> !p.getSession().isFinished());
                    if (hasActive) {
                        return Mono.error(new IllegalStateException("Participant is already in an active session."));
                    }
                }
                return getAuthenticatedUsername()
                    .map(Optional::of)
                    .defaultIfEmpty(Optional.empty())
                    .flatMap(optUsername -> {
                        Participant participant = new Participant();
                        participant.setParticipantId(participantUuid);
                        participant.setSession(newSession);
                        participant.setDisplayName(displayName);
                        participant.setRole(ParticipantRole.FACILITATOR);
                        participant.setLastSeen(LocalDateTime.now());
                        // Set username if available from auth or previous participations
                        String usernameToSet = optUsername.orElse(null);
                        if (usernameToSet == null && !existing.isEmpty()) {
                            existing.stream().filter(p -> p.getUsername() != null)
                                .findFirst().ifPresent(p -> participant.setUsername(p.getUsername()));
                        } else if (usernameToSet != null) {
                            participant.setUsername(usernameToSet);
                        }
                        return Mono.just(participantRepository.save(participant));
                    });
            });
    }
    
    /**
     * Retrieves the current participant based on the web exchange context (cookie for ID, security for username)
     * and a provided display name. If no participant exists for the identified ID, a new one is instantiated
     * but **not persisted** by this method.
     * 
     * This method is useful for operations that need to identify the current user (e.g., checking permissions,
     * displaying user-specific info) without necessarily creating a new session or assigning a role immediately.
     * Persistence of the participant should be handled by the caller if state changes (e.g., joining a session).
     *
     * @param exchange The current {@link ServerWebExchange}.
     * @param displayName The display name to use if a new participant needs to be identified or an existing one updated.
     * @return A {@link Mono} emitting the found or newly instantiated (but not saved) {@link Participant}.
     */
    public Mono<Participant> getCurrentParticipant(ServerWebExchange exchange, String displayName) {
        return getOrGenerateParticipantId(exchange)
            .flatMap(participantUuid -> getAuthenticatedUsername()
                .map(Optional::of).defaultIfEmpty(Optional.empty())
                .map(optUsername -> {
                    Participant shell = new Participant();
                    shell.setParticipantId(participantUuid);
                    shell.setDisplayName(displayName);
                    optUsername.ifPresent(shell::setUsername);
                    // Optionally, try to find a username from any past participation if not authenticated now
                    if (!optUsername.isPresent()) {
                        List<Participant> existing = participantRepository.findByParticipantId(participantUuid);
                        existing.stream().filter(p -> p.getUsername() != null)
                            .findFirst().ifPresent(p -> shell.setUsername(p.getUsername()));
                    }
                    return shell;
                })
            );
    }

    /**
     * Retrieves the participant ID from the {@link #PARTICIPANT_ID_COOKIE} in the {@link ServerWebExchange}.
     * If the cookie is not present, is empty, or contains an invalid UUID, a new time-ordered (UUIDv7-like)
     * participant ID is generated, set as a cookie in the response, and returned.
     *
     * @param exchange The current {@link ServerWebExchange} from which to read cookies and to which a new cookie may be added.
     * @return A {@link Mono} emitting the {@link UUID} of the participant.
     */
    Mono<UUID> getOrGenerateParticipantId(ServerWebExchange exchange) {
        HttpCookie cookie = exchange.getRequest().getCookies().getFirst(PARTICIPANT_ID_COOKIE);
        if (cookie != null && cookie.getValue() != null && !cookie.getValue().isEmpty()) {
            try {
                return Mono.just(UUID.fromString(cookie.getValue()));
            } catch (IllegalArgumentException e) {
                log.warn("Invalid UUID format in cookie {}: {}. Generating new one.", PARTICIPANT_ID_COOKIE, cookie.getValue(), e);
            }
        }
        UUID newParticipantId = UUID_V7_EPOCH_GENERATOR.generate();
        ResponseCookie responseCookie = ResponseCookie.from(PARTICIPANT_ID_COOKIE, newParticipantId.toString())
            .path("/")
            .maxAge(COOKIE_MAX_AGE)
            .httpOnly(true)
            .secure(exchange.getRequest().getURI().getScheme().equalsIgnoreCase("https"))
            .build();
        exchange.getResponse().addCookie(responseCookie);
        log.debug("Generated new participantId {} and set cookie.", newParticipantId);
        return Mono.just(newParticipantId);
    }

    /**
     * Retrieves the username of the currently authenticated principal from the Spring Security context.
     *
     * @return A {@link Mono} emitting the username if an authenticated principal is found,
     *         otherwise an empty {@link Mono}.
     */
    Mono<String> getAuthenticatedUsername() {
        return ReactiveSecurityContextHolder.getContext()
            .map(SecurityContext::getAuthentication)
            .filter(Authentication::isAuthenticated)
            .map(Authentication::getName)
            .switchIfEmpty(Mono.empty());
    }

    /**
     * Adds a user (identified via {@code exchange}) to an existing, persisted {@link RetroSession}
     * with the specified {@link ParticipantRole}. 
     * Enforces that a participant can only be in one active session at a time.
     * The caller is responsible for ensuring the provided {@code session} is a valid and persisted entity.
     * This method creates a new {@link Participant} record for the specified {@code session}, associating
     * the user (identified by their stable {@code participantId} from cookie/auth) with that session and role.
     *
     * @param exchange The current {@link ServerWebExchange}.
     * @param session The {@link RetroSession} to which the participant will be added/updated.
     * @param displayName The display name for the participant.
     * @param role The {@link ParticipantRole} to assign to the participant in this session.
     * @return A {@link Mono} emitting the persisted {@link Participant}.
     */
    @Transactional
    public Mono<Participant> addParticipantToSession(ServerWebExchange exchange, RetroSession session, String displayName, ParticipantRole role) {
        if (role == null) {
            return Mono.error(new IllegalArgumentException("Participant role cannot be null"));
        }
        if (session == null || session.getId() == null) {
            return Mono.error(new IllegalArgumentException("Session and session ID cannot be null"));
        }
        return getOrGenerateParticipantId(exchange)
            .flatMap(participantUuid -> {
                // Enforce only one active session per participant
                List<Participant> existing = participantRepository.findByParticipantId(participantUuid);
                if (!existing.isEmpty()) {
                    boolean hasActive = existing.stream().anyMatch(p -> !p.getSession().isFinished());
                    if (hasActive) {
                        return Mono.error(new IllegalStateException("Participant is already in an active session."));
                    }
                }
                return getAuthenticatedUsername()
                    .map(Optional::of)
                    .defaultIfEmpty(Optional.empty())
                    .flatMap(optUsername -> {
                        Participant participant = new Participant();
                        participant.setParticipantId(participantUuid);
                        participant.setSession(session);
                        participant.setDisplayName(displayName);
                        participant.setRole(role);
                        participant.setLastSeen(LocalDateTime.now());
                        String usernameToSet = optUsername.orElse(null);
                        if (usernameToSet == null && !existing.isEmpty()) {
                            existing.stream().filter(p -> p.getUsername() != null)
                                .findFirst().ifPresent(p -> participant.setUsername(p.getUsername()));
                        } else if (usernameToSet != null) {
                            participant.setUsername(usernameToSet);
                        }
                        return Mono.just(participantRepository.save(participant));
                    });
            });
    }

    /**
     * Retrieves a {@link Participant} for the current user (identified via {@code exchange})
     * within a specific {@code sessionId}.
     *
     * @param exchange The current {@link ServerWebExchange} to identify the participant.
     * @param sessionId The ID of the session to look for the participant in.
     * @return A {@link Mono} emitting an {@link Optional} containing the {@link Participant}
     *         if found, or an empty {@link Optional} otherwise.
     */
    private Mono<Optional<Participant>> getParticipantInSession(ServerWebExchange exchange, UUID sessionId) {
        return getOrGenerateParticipantId(exchange)
            .flatMap(participantUuid -> 
                Mono.fromCallable(() -> {
                    ParticipantId pk = new ParticipantId(participantUuid, sessionId);
                    return participantRepository.findById(pk);
                })
            );
    }

    /**
     * Retrieves all participants associated with a given session ID.
     *
     * @param sessionId The {@link UUID} of the {@link RetroSession}.
     * @return A list of {@link Participant}s in the session. Can be empty if the session has no participants or does not exist.
     */
    public List<Participant> getSessionParticipants(UUID sessionId) {
        return participantRepository.findBySession_Id(sessionId);
    }

    /**
     * Checks if the current participant (identified via {@code exchange}) is the FACILITATOR
     * of the specified session.
     *
     * @param exchange The current {@link ServerWebExchange}.
     * @param sessionId The {@link UUID} of the {@link RetroSession}.
     * @return A {@link Mono} emitting {@code true} if the participant is the facilitator, {@code false} otherwise.
     */
    public Mono<Boolean> isFacilitator(ServerWebExchange exchange, UUID sessionId) {
        return getParticipantInSession(exchange, sessionId)
            .map(optParticipant -> optParticipant
                .map(p -> p.getRole() == ParticipantRole.FACILITATOR)
                .orElse(false)
            );
    }

    /**
     * Checks if the current participant (identified via {@code exchange}) is participating
     * in the specified session.
     *
     * @param exchange The current {@link ServerWebExchange}.
     * @param sessionId The {@link UUID} of the {@link RetroSession}.
     * @return A {@link Mono} emitting {@code true} if the participant is in the session, {@code false} otherwise.
     */
    public Mono<Boolean> isParticipating(ServerWebExchange exchange, UUID sessionId) {
         return getParticipantInSession(exchange, sessionId)
            .map(Optional::isPresent);
    }
   
    /**
     * Updates the {@code lastSeen} timestamp for the current participant in the specified session.
     * If the participant is not found in the session, a warning is logged and no update occurs.
     * The participant is identified using the cookie in the {@link ServerWebExchange}.
     *
     * @param exchange The current {@link ServerWebExchange}.
     * @param sessionId The {@link UUID} of the {@link RetroSession} for which to update the last seen time.
     * @return A {@link Mono} that completes when the operation is done.
     */
    @Transactional
    public Mono<Void> updateLastSeen(ServerWebExchange exchange, UUID sessionId) {
        // This method should update lastSeen for the user in a *specific* session.
        return getOrGenerateParticipantId(exchange)
            .flatMap(participantUuid -> {
                ParticipantId pk = new ParticipantId(participantUuid, sessionId);
                Optional<Participant> optParticipant = participantRepository.findById(pk);
                if (optParticipant.isPresent()) {
                    Participant p = optParticipant.get();
                    p.setLastSeen(LocalDateTime.now());
                    participantRepository.save(p);
                    log.debug("Updated lastSeen for participant {} in session {}", participantUuid, sessionId);
                } else {
                    log.warn("Attempted to update lastSeen for participant {} in session {}, but participant not found.", participantUuid, sessionId);
                }
                return Mono.empty(); 
            }).then();
    }
}
