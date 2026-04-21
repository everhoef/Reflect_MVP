package direct.reflect.facilitator.integration;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitUntilState;
import direct.reflect.facilitator.configurator.ComponentType;
import direct.reflect.facilitator.configurator.RetroStep;
import direct.reflect.facilitator.configurator.RetroStepRepository;
import direct.reflect.facilitator.configurator.RetroTemplate;
import direct.reflect.facilitator.facilitation.session.RetroPhase;
import direct.reflect.facilitator.facilitation.session.RetroSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import direct.reflect.facilitator.organization.Organization;
import direct.reflect.facilitator.organization.OrganizationRepository;
import direct.reflect.facilitator.organization.Team;
import direct.reflect.facilitator.organization.TeamMember;
import direct.reflect.facilitator.organization.TeamMemberRepository;
import direct.reflect.facilitator.organization.TeamRepository;
import direct.reflect.facilitator.organization.TeamRole;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Escalation Voting Browser Regression Tests")
class EscalationVotingBrowserRegressionTest extends BaseIntegrationTest {

    private static final UUID OIDC_USER_ID_NAMESPACE = UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8");

    @Autowired
    private RetroStepRepository stepRepository;
    
    @Autowired
    private OrganizationRepository organizationRepository;
    
    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private TeamMemberRepository teamMemberRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    @Timeout(300)
    @DisplayName("Should create a team-backed escalation and propagate vote state across isolated browser contexts")
    void shouldCreateTeamBackedEscalationAndPropagateVoteStateAcrossIsolatedBrowserContexts() {
        BrowserContext facilitatorContext = createMonitoredContext();
        Page facilitatorPage = facilitatorContext.newPage();

        BrowserContext bobContext = createMonitoredContext();
        Page bobPage = bobContext.newPage();

        BrowserContext carolContext = createMonitoredContext();
        Page carolPage = carolContext.newPage();

        String managerUsername = "manager-" + UUID.randomUUID().toString().substring(0, 8);
        String managerEmail = managerUsername + "@example.com";
        String actionTitle = "Stabilize release train handoff";
        String problemDescription = "Cross-team dependency blocks release commitments";

        try {
            waitForServerReady();

            logTestProgress("SETUP", 1, 8, "Creating facilitator's managed team");
            Team managedTeam = createManagedTeamForUsername("Release Platform", managerUsername);

            logTestProgress("SETUP", 2, 8, "Authenticating isolated facilitator and participants");
            authenticateAsOAuth2User(facilitatorPage, managerUsername, "Alice Manager", managerEmail);
            authenticateAsGuest(bobPage, "Bob");
            authenticateAsGuest(carolPage, "Carol");

            logTestProgress("SETUP", 3, 8, "Creating team-backed session and joining participants");
            String sessionId = createRetroSession(facilitatorPage, "Escalation Voting Test");

            RetroSession createdSession = retroSessionRepository.findById(UUID.fromString(sessionId))
                    .orElseThrow(() -> new IllegalStateException("Session not found: " + sessionId));
            Team sessionTeam = readField(createdSession, "team", Team.class);
            assertNotNull(sessionTeam, "Facilitator-created session should auto-link to the manager's single team");
            assertEquals(readEntityIdentifier(managedTeam), readEntityIdentifier(sessionTeam),
                    "Facilitator-created session should be truthfully team-backed before escalation");

            joinRetroSession(bobPage, sessionId);
            joinRetroSession(carolPage, sessionId);
            waitForSseConnection(facilitatorPage, UUID.fromString(sessionId));
            waitForSseConnection(bobPage, UUID.fromString(sessionId));
            waitForSseConnection(carolPage, UUID.fromString(sessionId));

            logTestProgress("SETUP", 4, 8, "Starting session and fast-forwarding to SMART Action Builder");
            startRetroSession(facilitatorPage, sessionId);
            enableEscalationAndFastForwardToSmartActionBuilder(sessionId);

            String retroUrl = baseUrl + "/retro/" + sessionId;
            facilitatorPage.navigate(retroUrl, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
            bobPage.navigate(retroUrl, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
            carolPage.navigate(retroUrl, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));

            waitForAllPagesElement("[data-testid='retro-content']", SSE_PROPAGATION_TIMEOUT_MS,
                    facilitatorPage, bobPage, carolPage);
            waitForSseConnection(facilitatorPage, UUID.fromString(sessionId));
            waitForSseConnection(bobPage, UUID.fromString(sessionId));
            waitForSseConnection(carolPage, UUID.fromString(sessionId));
            waitForAllPagesElement("text='Create SMART Action'", SSE_PROPAGATION_TIMEOUT_MS,
                    facilitatorPage, bobPage, carolPage);

            logTestProgress("TEST", 5, 8, "Creating canonical escalated action on the active step");
            fillElement(facilitatorPage, "[data-testid='what-input']", actionTitle);
            fillElement(facilitatorPage, "[data-testid='who-input']", "Release Platform Team");
            fillElement(facilitatorPage, "[data-testid='due-date-input']", "2026-12-31");
            fillElement(facilitatorPage, "[data-testid='success-criteria-input']", "Cross-team blockers are resolved before planning");

            facilitatorPage.check("[data-testid='escalation-toggle']");
            fillElement(facilitatorPage, "[data-testid='problem-description-input']", problemDescription);
            clickElement(facilitatorPage, "[data-testid='create-action-btn']");

            waitForAllPagesElement("text='" + actionTitle + "'", SSE_PROPAGATION_TIMEOUT_MS,
                    facilitatorPage, bobPage, carolPage);
            waitForAllPagesElement("text='Escalated to Management'", SSE_PROPAGATION_TIMEOUT_MS,
                    facilitatorPage, bobPage, carolPage);
            waitForVoteCountOnAllPages(0, facilitatorPage, bobPage, carolPage);
            assertThresholdBadgeAbsent(facilitatorPage, bobPage, carolPage);

            logTestProgress("TEST", 6, 8, "Bob votes and the count propagates without reaching threshold");
            clickVoteButtonForCurrentCount(bobPage, 0);
            waitForElement(bobPage, "button:has-text('Unvote (1)')", SSE_PROPAGATION_TIMEOUT_MS);
            waitForVoteCountOnAllPages(1, facilitatorPage, bobPage, carolPage);
            assertThresholdBadgeAbsent(facilitatorPage, bobPage, carolPage);

            logTestProgress("TEST", 7, 8, "Carol votes and threshold state propagates across users");
            clickVoteButtonForCurrentCount(carolPage, 1);
            waitForElement(carolPage, "button:has-text('Unvote (2)')", SSE_PROPAGATION_TIMEOUT_MS);
            waitForVoteCountOnAllPages(2, facilitatorPage, bobPage, carolPage);
            waitForAllPagesElement("text='Threshold Met'", SSE_PROPAGATION_TIMEOUT_MS,
                    facilitatorPage, bobPage, carolPage);

            logTestProgress("TEST", 8, 8, "Bob unvotes and threshold state clears while Carol stays voted");
            clickElement(bobPage, "button:has-text('Unvote (2)')", SSE_PROPAGATION_TIMEOUT_MS);
            waitForElement(bobPage, "button:has-text('Vote (1)')", SSE_PROPAGATION_TIMEOUT_MS);
            waitForElement(carolPage, "button:has-text('Unvote (1)')", SSE_PROPAGATION_TIMEOUT_MS);
            waitForVoteCountOnAllPages(1, facilitatorPage, bobPage, carolPage);
            waitForThresholdBadgeToDisappear(facilitatorPage, bobPage, carolPage);
            assertThresholdBadgeAbsent(facilitatorPage, bobPage, carolPage);

        } catch (Exception e) {
            reportTestFailure(facilitatorPage, "Canonical Escalation Voting", e);
            throw e;
        } finally {
            facilitatorContext.close();
            bobContext.close();
            carolContext.close();
        }
    }

    private void waitForServerReady() {
        recordActivity("waitForServerReady");
        long deadline = System.currentTimeMillis() + 30_000;
        Exception lastFailure = null;
        while (System.currentTimeMillis() < deadline) {
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL(baseUrl + "/login").openConnection();
                connection.setConnectTimeout(1_000);
                connection.setReadTimeout(3_000);
                int status = connection.getResponseCode();
                connection.disconnect();
                if (status < 500) {
                    recordActivity("✓ Server ready");
                    return;
                }
            } catch (Exception readinessFailure) {
                lastFailure = readinessFailure;
            }

            try {
                Thread.sleep(500);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        throw new AssertionError("Server did not become ready within 30 seconds"
                + (lastFailure == null ? "" : ": " + lastFailure.getMessage()));
    }

    private Team createManagedTeamForUsername(String teamName, String username) {
        recordActivity("createManagedTeamForUsername: " + teamName + " -> " + username);

        Organization organization = new Organization();
        writeField(organization, "name", teamName + " Org");
        writeField(organization, "slug", teamName.toLowerCase().replace(" ", "-") + "-org-" + UUID.randomUUID().toString().substring(0, 8));
        Organization savedOrganization = organizationRepository.saveAndFlush(organization);

        Team team = new Team();
        writeField(team, "name", teamName);
        writeField(team, "organization", savedOrganization);
        Team savedTeam = teamRepository.saveAndFlush(team);

        TeamMember managerMembership = new TeamMember();
        writeField(managerMembership, "team", savedTeam);
        writeField(managerMembership, "userId", resolveOidcUserId(username));
        writeField(managerMembership, "role", TeamRole.MANAGER);
        teamMemberRepository.saveAndFlush(managerMembership);

        return savedTeam;
    }

    private void enableEscalationAndFastForwardToSmartActionBuilder(String sessionId) {
        recordActivity("enableEscalationAndFastForwardToSmartActionBuilder: " + sessionId);

        RetroSession session = retroSessionRepository.findById(UUID.fromString(sessionId))
                .orElseThrow(() -> new IllegalStateException("Session not found: " + sessionId));

        RetroTemplate template = templateRepository.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No templates found"));
        if (template == null) {
            throw new IllegalStateException("Session template not found for session: " + sessionId);
        }

        List<RetroStep> steps = stepRepository.findByRetroStageOrderByOrderIndexAsc(template.getStageForPhase(RetroPhase.DECIDE_ACTIONS));
        int smartActionStepIndex = -1;

        for (int index = 0; index < steps.size(); index++) {
            RetroStep step = steps.get(index);
            ComponentType componentType = readField(step, "componentType", ComponentType.class);
            Map<String, Object> componentConfig = readRequiredMap(step, "componentConfig");
            if (componentType == ComponentType.SMART_ACTION_BUILDER) {
                assertEquals(Boolean.TRUE, componentConfig.get("allowEscalation"), "SMART_ACTION_BUILDER should have allowEscalation=true from retrospective_steps.csv import");
                // no persistence needed; config is imported from retrospective_steps.csv
                if (smartActionStepIndex < 0) {
                    smartActionStepIndex = index;
                }
            }
        }

        if (smartActionStepIndex < 0) {
            throw new IllegalStateException("SMART_ACTION_BUILDER step not found for session template");
        }

        writeField(session, "phase", RetroPhase.DECIDE_ACTIONS);
        writeField(session, "currentStepIndex", smartActionStepIndex);
        retroSessionRepository.save(session);
    }

    private void clickVoteButtonForCurrentCount(Page page, int voteCount) {
        String voteSelector = "button:has-text('Vote (" + voteCount + ")')";
        String toggleSelector = "button:has-text('Toggle Vote (" + voteCount + ")')";

        if (page.locator(voteSelector).count() > 0) {
            clickElement(page, voteSelector, SSE_PROPAGATION_TIMEOUT_MS);
            return;
        }

        if (page.locator(toggleSelector).count() > 0) {
            clickElement(page, toggleSelector, SSE_PROPAGATION_TIMEOUT_MS);
            return;
        }

        throw new AssertionError("No vote button found for current count " + voteCount + " on page " + page.url());
    }

    private void waitForThresholdBadgeToDisappear(Page... pages) {
        for (Page page : pages) {
            page.waitForFunction(
                    "() => !document.body.innerText.includes('Threshold Met')",
                    null,
                    new Page.WaitForFunctionOptions().setTimeout(SSE_PROPAGATION_TIMEOUT_MS));
        }
    }

    private void assertThresholdBadgeAbsent(Page... pages) {
        for (Page page : pages) {
            assertEquals(0, page.locator("text='Threshold Met'").count(),
                    "Threshold badge should be absent on page: " + page.url());
        }
    }

    private void waitForVoteCountOnAllPages(int expectedCount, Page... pages) {
        for (Page page : pages) {
            waitForElement(page, "button:has-text('(" + expectedCount + ")')", SSE_PROPAGATION_TIMEOUT_MS);
        }
    }

    private UUID resolveOidcUserId(String username) {
        return UUID.nameUUIDFromBytes((OIDC_USER_ID_NAMESPACE + username).getBytes(StandardCharsets.UTF_8));
    }

    private void writeField(Object target, String fieldName, Object value) {
        ReflectionTestUtils.setField(target, fieldName, value);
    }

    private <T> T readField(Object target, String fieldName, Class<T> fieldType) {
        Object value = ReflectionTestUtils.getField(target, fieldName);
        if (value == null) {
            return null;
        }
        return fieldType.cast(value);
    }

    private UUID readEntityIdentifier(Object entity) {
        Object value = entityManagerFactory.getPersistenceUnitUtil().getIdentifier(entity);
        if (value == null) {
            throw new IllegalStateException("Expected entity identifier to be populated on " + entity.getClass().getSimpleName());
        }
        return (UUID) value;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readRequiredMap(Object target, String fieldName) {
        Object value = ReflectionTestUtils.getField(target, fieldName);
        if (value == null) {
            throw new IllegalStateException("Expected map field '" + fieldName + "' to be populated on " + target.getClass().getSimpleName());
        }
        return (Map<String, Object>) value;
    }
}
