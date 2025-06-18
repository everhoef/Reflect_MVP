// src/test/java/direct/reflect/facilitator/service/ParticipantServiceTest.java
package direct.reflect.facilitator.service;

import direct.reflect.facilitator.domain.entity.Participant;
import direct.reflect.facilitator.domain.entity.RetroSession;
import direct.reflect.facilitator.domain.enums.ParticipantRole;
import direct.reflect.facilitator.repository.ParticipantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpCookie;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Collections;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ParticipantServiceTest {

    @Mock
    private ParticipantRepository participantRepository;

    @Mock
    private RetroSessionService retroSessionService;

    @InjectMocks
    private ParticipantService participantService;

    private ServerWebExchange mockExchange;
    private MockServerHttpRequest mockRequest;
    private MockedStatic<ReactiveSecurityContextHolder> reactiveSecurityContextHolderMockedStatic;

    @BeforeEach
    void setUp() {
        mockRequest = MockServerHttpRequest.get("http://localhost/").build(); // Ensure scheme is set
        mockExchange = MockServerWebExchange.from(mockRequest);
        reactiveSecurityContextHolderMockedStatic = Mockito.mockStatic(ReactiveSecurityContextHolder.class);
    }

    @AfterEach
    void tearDown() {
        if (reactiveSecurityContextHolderMockedStatic != null) {
            reactiveSecurityContextHolderMockedStatic.close();
        }
    }

    @Test
    void getOrGenerateParticipantId_noCookie_shouldGenerateUuidAndSetCookie() {
        // Arrange: mockExchange is already set up with no cookies and a scheme in setUp()

        // Act & Assert
        StepVerifier.create(participantService.getOrGenerateParticipantId(mockExchange))
            .expectNextMatches(uuid -> {
                assertNotNull(uuid);
                ResponseCookie cookie = mockExchange.getResponse().getCookies().getFirst(ParticipantService.PARTICIPANT_ID_COOKIE);
                assertNotNull(cookie);
                assertEquals(ParticipantService.PARTICIPANT_ID_COOKIE, cookie.getName());
                assertEquals(uuid.toString(), cookie.getValue());
                assertEquals(ParticipantService.COOKIE_MAX_AGE, cookie.getMaxAge().getSeconds());
                assertTrue(cookie.isHttpOnly());
                assertFalse(cookie.isSecure()); // Should be false for http
                return true;
            })
            .verifyComplete();
    }
    
    @Test
    void getOrGenerateParticipantId_noCookie_secureRequest_shouldSetSecureCookie() {
        // Arrange
        mockRequest = MockServerHttpRequest.get("https://localhost/").build(); // HTTPS request
        mockExchange = MockServerWebExchange.from(mockRequest);

        // Act & Assert
        StepVerifier.create(participantService.getOrGenerateParticipantId(mockExchange))
            .expectNextMatches(uuid -> {
                assertNotNull(uuid);
                ResponseCookie cookie = mockExchange.getResponse().getCookies().getFirst(ParticipantService.PARTICIPANT_ID_COOKIE);
                assertNotNull(cookie);
                assertTrue(cookie.isSecure()); // Should be true for https
                return true;
            })
            .verifyComplete();
    }


    @Test
    void getOrGenerateParticipantId_existingCookie_shouldReturnCookieUuidAndNotSetCookie() {
        // Arrange
        UUID existingUuid = UUID.randomUUID();
        mockRequest = MockServerHttpRequest.get("http://localhost/") 
            .cookie(new HttpCookie(ParticipantService.PARTICIPANT_ID_COOKIE, existingUuid.toString()))
            .build();
        mockExchange = MockServerWebExchange.from(mockRequest);

        // Act & Assert
        StepVerifier.create(participantService.getOrGenerateParticipantId(mockExchange))
            .expectNext(existingUuid)
            .verifyComplete();
        
        assertNull(mockExchange.getResponse().getCookies().getFirst(ParticipantService.PARTICIPANT_ID_COOKIE));
    }

    @Test
    void createAndAssignFacilitatorForSession_anonymousUser_noCookie_createsSessionFacilitatorAndSetsCookie() {
        String sessionName = "Retro-Alpha";
        String displayName = "Facilitator Anon";
        RetroSession newSession = new RetroSession();
        UUID sessionId = UUID.randomUUID();
        newSession.setId(sessionId);
        newSession.setName(sessionName);

        when(retroSessionService.createNewSession(sessionName)).thenReturn(newSession);
        reactiveSecurityContextHolderMockedStatic.when(ReactiveSecurityContextHolder::getContext).thenReturn(Mono.empty());
        when(participantRepository.findByParticipantId(any(UUID.class))).thenReturn(Collections.emptyList());
        when(participantRepository.save(any(Participant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(participantService.createAndAssignFacilitatorForSession(sessionName, displayName, mockExchange))
            .expectNextMatches(participant -> {
                assertNotNull(participant.getParticipantId(), "Participant UUID part of ID should be set");
                assertEquals(displayName, participant.getDisplayName());
                assertNull(participant.getUsername());
                assertEquals(ParticipantRole.FACILITATOR, participant.getRole());
                assertNotNull(participant.getSession(), "Session part of ID should be set");
                assertEquals(newSession, participant.getSession());
                assertEquals(sessionId, participant.getSession().getId(), "Session ID should match");
                assertNotNull(participant.getLastSeen());
                
                ResponseCookie cookie = mockExchange.getResponse().getCookies().getFirst(ParticipantService.PARTICIPANT_ID_COOKIE);
                assertNotNull(cookie);
                assertEquals(participant.getParticipantId().toString(), cookie.getValue());
                return true;
            })
            .verifyComplete();

        verify(participantRepository).save(any(Participant.class));
    }

    @Test
    void createAndAssignFacilitatorForSession_anonymousUser_withCookie_createsSessionFacilitatorUsesCookieId() {
        String sessionName = "Retro-Beta";
        String displayName = "Facilitator Cookie";
        UUID existingParticipantUuid = UUID.randomUUID();

        mockRequest = MockServerHttpRequest.get("http://localhost/") 
            .cookie(new HttpCookie(ParticipantService.PARTICIPANT_ID_COOKIE, existingParticipantUuid.toString()))
            .build();
        mockExchange = MockServerWebExchange.from(mockRequest);
        
        RetroSession newSession = new RetroSession();
        UUID newSessionId = UUID.randomUUID();
        newSession.setId(newSessionId);
        newSession.setName(sessionName);

        when(retroSessionService.createNewSession(sessionName)).thenReturn(newSession);
        reactiveSecurityContextHolderMockedStatic.when(ReactiveSecurityContextHolder::getContext).thenReturn(Mono.empty());

        Participant profileShell = new Participant();
        profileShell.setParticipantId(existingParticipantUuid);
        profileShell.setDisplayName("Old Name");
        RetroSession oldSession = new RetroSession();
        UUID oldSessionId = UUID.randomUUID();
        oldSession.setId(oldSessionId);
        oldSession.setPhase(direct.reflect.facilitator.domain.enums.RetroPhase.COMPLETED); 
        profileShell.setSession(oldSession);

        when(participantRepository.findByParticipantId(existingParticipantUuid)).thenReturn(Collections.singletonList(profileShell));
        when(participantRepository.save(any(Participant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(participantService.createAndAssignFacilitatorForSession(sessionName, displayName, mockExchange))
            .expectNextMatches(participant -> {
                assertEquals(existingParticipantUuid, participant.getParticipantId(), "Participant UUID should match cookie");
                assertEquals(displayName, participant.getDisplayName());
                assertNull(participant.getUsername());
                assertEquals(ParticipantRole.FACILITATOR, participant.getRole());
                assertNotNull(participant.getSession(), "Session part of ID should be set");
                assertEquals(newSession, participant.getSession());
                assertEquals(newSessionId, participant.getSession().getId(), "Session ID should match new session");
                assertNull(mockExchange.getResponse().getCookies().getFirst(ParticipantService.PARTICIPANT_ID_COOKIE), "Cookie should not be reset");
                return true;
            })
            .verifyComplete();

        verify(participantRepository).save(any(Participant.class));
    }

    @Test
    void createAndAssignFacilitatorForSession_authenticatedUser_noCookie_noExistingProfile_createsSessionFacilitatorSetsUsernameAndCookie() {
        String sessionName = "Retro-Gamma";
        String displayName = "Auth Facilitator";
        String username = "testuser";

        RetroSession newSession = new RetroSession();
        UUID newSessionId = UUID.randomUUID();
        newSession.setId(newSessionId);
        newSession.setName(sessionName);

        // Ensure the Authentication object is authenticated
        SecurityContext securityContext = new SecurityContextImpl(
            new UsernamePasswordAuthenticationToken(username, null, Collections.emptyList()) // Use constructor that sets authenticated = true
        );
        reactiveSecurityContextHolderMockedStatic.when(ReactiveSecurityContextHolder::getContext).thenReturn(Mono.just(securityContext));
        
        when(retroSessionService.createNewSession(sessionName)).thenReturn(newSession);
        when(participantRepository.findByParticipantId(any(UUID.class))).thenReturn(Collections.emptyList());
        when(participantRepository.save(any(Participant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(participantService.createAndAssignFacilitatorForSession(sessionName, displayName, mockExchange))
            .expectNextMatches(participant -> {
                assertNotNull(participant.getParticipantId(), "Participant UUID part of ID should be set");
                assertEquals(displayName, participant.getDisplayName());
                assertEquals(username, participant.getUsername());
                assertEquals(ParticipantRole.FACILITATOR, participant.getRole());
                assertNotNull(participant.getSession(), "Session part of ID should be set");
                assertEquals(newSession, participant.getSession());
                assertEquals(newSessionId, participant.getSession().getId(), "Session ID should match");
                
                ResponseCookie cookie = mockExchange.getResponse().getCookies().getFirst(ParticipantService.PARTICIPANT_ID_COOKIE);
                assertNotNull(cookie);
                assertEquals(participant.getParticipantId().toString(), cookie.getValue());
                return true;
            })
            .verifyComplete();
            
        verify(participantRepository).save(any(Participant.class));
    }
    
    @Test
    void createAndAssignFacilitatorForSession_authenticatedUser_withProfile_usesProfileId() {
        String sessionName = "Retro-Delta";
        String displayName = "Known Auth Facilitator";
        String username = "knownuser";

        RetroSession newSession = new RetroSession();
        UUID newSessionId = UUID.randomUUID();
        newSession.setId(newSessionId);
        newSession.setName(sessionName);

        // Ensure the Authentication object is authenticated
        SecurityContext securityContext = new SecurityContextImpl(
            new UsernamePasswordAuthenticationToken(username, null, Collections.emptyList()) // Use constructor that sets authenticated = true
        );
        reactiveSecurityContextHolderMockedStatic.when(ReactiveSecurityContextHolder::getContext).thenReturn(Mono.just(securityContext));
        
        when(participantRepository.findByParticipantId(any(UUID.class))).thenReturn(Collections.emptyList());
        
        when(retroSessionService.createNewSession(sessionName)).thenReturn(newSession);
        when(participantRepository.save(any(Participant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(participantService.createAndAssignFacilitatorForSession(sessionName, displayName, mockExchange))
            .expectNextMatches(participant -> {
                assertNotNull(participant.getParticipantId(), "Participant UUID part of ID should be set"); 
                assertEquals(displayName, participant.getDisplayName());
                assertEquals(username, participant.getUsername()); 
                assertEquals(ParticipantRole.FACILITATOR, participant.getRole());
                assertNotNull(participant.getSession(), "Session part of ID should be set");
                assertEquals(newSession, participant.getSession());
                assertEquals(newSessionId, participant.getSession().getId(), "Session ID should match");
                
                ResponseCookie cookie = mockExchange.getResponse().getCookies().getFirst(ParticipantService.PARTICIPANT_ID_COOKIE);
                assertNotNull(cookie);
                assertEquals(participant.getParticipantId().toString(), cookie.getValue()); 
                return true;
            })
            .verifyComplete();
            
        verify(participantRepository).save(any(Participant.class));
    }

    @Test
    void createAndAssignFacilitatorForSession_anonymousUser_createsSessionAndFacilitator() {
        String sessionName = "Simple Retro";
        String displayName = "Simple Facil";
        RetroSession newSession = new RetroSession();
        UUID newSessionId = UUID.randomUUID();
        newSession.setId(newSessionId);
        newSession.setName(sessionName);

        when(retroSessionService.createNewSession(sessionName)).thenReturn(newSession);
        reactiveSecurityContextHolderMockedStatic.when(ReactiveSecurityContextHolder::getContext).thenReturn(Mono.empty());
        when(participantRepository.findByParticipantId(any(UUID.class))).thenReturn(Collections.emptyList());
        when(participantRepository.save(any(Participant.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        StepVerifier.create(participantService.createAndAssignFacilitatorForSession(sessionName, displayName, mockExchange))
            .expectNextMatches(participant -> {
                 assertNotNull(participant.getParticipantId(), "Participant UUID part of ID should be set");
                 assertEquals(displayName, participant.getDisplayName());
                 assertNull(participant.getUsername());
                 assertEquals(ParticipantRole.FACILITATOR, participant.getRole());
                 assertNotNull(participant.getSession(), "Session part of ID should be set");
                 assertEquals(newSession, participant.getSession());
                 assertEquals(newSessionId, participant.getSession().getId(), "Session ID should match");
                 assertNotNull(participant.getLastSeen());
                
                 ResponseCookie cookie = mockExchange.getResponse().getCookies().getFirst(ParticipantService.PARTICIPANT_ID_COOKIE);
                 assertNotNull(cookie, "Cookie should be set for new anonymous facilitator");
                 assertEquals(participant.getParticipantId().toString(), cookie.getValue());
                 return true;
            })
            .verifyComplete();

        verify(participantRepository).save(any(Participant.class));
    }
}