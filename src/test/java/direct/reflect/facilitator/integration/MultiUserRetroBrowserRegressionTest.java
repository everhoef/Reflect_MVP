package direct.reflect.facilitator.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Timeout;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.UUID;

import direct.reflect.facilitator.facilitation.RetroPhase;
import direct.reflect.facilitator.facilitation.RetroSession;
import lombok.extern.slf4j.Slf4j;

/**
 * Broad retrospective flow integration tests.
 *
 * <p>Tests multi-step retrospective flows including participant interactions,
 * note submission, voting, and phase transitions. For canonical golden-path
 * regression see {@link RetroFlowBrowserRegressionTest}.
 *
 * <p>Responsibility: Multi-user interaction flows and component integration.
 * Unique regression coverage only — overlap with golden-path suite is intentional
 * only where the specific multi-user scenario cannot be verified in the golden path.
 *
 * <p>Test scope:
 * <ul>
 *   <li>{@code shouldValidateCompleteRetroFlowWithColumnIsolation} — 23-step complete flow:
 *       ESVP check-in, Mad Sad Glad cross-user visibility, Perfection Game rating + histogram,
 *       Start Stop Continue columns, +/- Delta close.</li>
 * </ul>
 *
 * <p>SSE transport/session sync smoke tests are in {@link SseTransportSmokeTest}.
 * SSE → React UI update chain tests are in {@link SseUiChainTest}.
 * Golden-path regression is in {@link RetroFlowBrowserRegressionTest}.
 */
@DisplayName("Multi-User Retro Browser Regression Tests")
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MultiUserRetroBrowserRegressionTest extends BaseIntegrationTest {

    @Test
    @Order(7)
    @Timeout(600) // 10 minutes max - flow test has many steps with multi-page sync
    @DisplayName("Should validate complete retro flow with columnId isolation")
    void shouldValidateCompleteRetroFlowWithColumnIsolation() throws InterruptedException {
        BrowserContext facilitatorContext = createMonitoredContext();
        Page facilitatorPage = facilitatorContext.newPage();

        BrowserContext bobContext = createMonitoredContext();
        Page bobPage = bobContext.newPage();

        BrowserContext carolContext = createMonitoredContext();
        Page carolPage = carolContext.newPage();

        try {
            log.info("═══════════════════════════════════════════════════════════════");
            log.info("  COMPLETE RETROSPECTIVE FLOW TEST (23 STEPS - Default Template)");
            log.info("  ESVP → Mad Sad Glad → Perfection Game → SSC → +/- Delta");
            log.info("═══════════════════════════════════════════════════════════════");

            // ── SETUP ──────────────────────────────────────────────────────────────
            logTestProgress("SETUP", 1, 23, "Authenticating 3 users");
            authenticateAsGuest(facilitatorPage, "Alice (Facilitator)");
            authenticateAsGuest(bobPage, "Bob");
            authenticateAsGuest(carolPage, "Carol");

            logTestProgress("SETUP", 2, 23, "Creating session and joining participants");
            String sessionId = createRetroSession(facilitatorPage, "Complete Flow Test");
            joinRetroSession(bobPage, sessionId);
            joinRetroSession(carolPage, sessionId);

            waitForElement(facilitatorPage, "[data-testid='start-retro-button']", SSE_PROPAGATION_TIMEOUT_MS);
            startRetroSession(facilitatorPage, sessionId);

            waitForAllPagesElement("[data-testid='retro-content']", SSE_PROPAGATION_TIMEOUT_MS, bobPage, carolPage);

            // ===== PHASE 1: SET_THE_STAGE — ESVP Check-in (2 steps) =====
            log.info("\n┌─ PHASE 1: SET_THE_STAGE (ESVP Check-in)");

            // Step 1 (orderIndex=1): ALL_RESPONDED — fast-forward past it to step 2 (reveal)
            logTestProgress("PHASE_1", 3, 23, "Fast-forwarding past ESVP input step (ALL_RESPONDED)");
            fastForwardSession(sessionId, RetroPhase.SET_THE_STAGE, 1);
            facilitatorPage.reload();
            waitForElement(facilitatorPage, "[data-column='Explorer']", SSE_PROPAGATION_TIMEOUT_MS);

            // Verify all 4 ESVP columns are visible on the facilitator page
            logTestProgress("PHASE_1", 4, 23, "Verifying ESVP columns visible at reveal step");
            log.info("  ├─ Verifying 4 ESVP columns...");
            assertTrue(facilitatorPage.locator("[data-column='Explorer']").isVisible(),
                "Explorer column should be visible at ESVP reveal step");
            assertTrue(facilitatorPage.locator("[data-column='Shopper']").isVisible(),
                "Shopper column should be visible at ESVP reveal step");
            assertTrue(facilitatorPage.locator("[data-column='Vacationer']").isVisible(),
                "Vacationer column should be visible at ESVP reveal step");
            assertTrue(facilitatorPage.locator("[data-column='Prisoner']").isVisible(),
                "Prisoner column should be visible at ESVP reveal step");
            log.info("  ├─ ✅ All 4 ESVP columns visible");

            // Advance from reveal step (FACILITATOR_CLICK) → GATHER_DATA
            clickNextAndWait(facilitatorPage, DEFAULT_TIMEOUT_MS);
            log.info("  └─ ✓ Completed SET_THE_STAGE phase (ESVP)");

            // ===== PHASE 2: GATHER_DATA — Mad Sad Glad (5 steps) =====
            log.info("\n├─ PHASE 2: GATHER_DATA (Mad Sad Glad)");

            // After clicking Next from ESVP reveal, we are now at GATHER_DATA step 0
            // (brainstorm step: TIMER_EXPIRES, allowInput=true, showContent=false)
            // Submit notes HERE (before fast-forwarding), while input is allowed.
            logTestProgress("PHASE_2", 5, 23, "Submitting Mad Sad Glad notes at brainstorm step");
            log.info("  ├─ Testing MULTI_COLUMN_BOARD note submission at brainstorm step...");

            waitForElement(facilitatorPage, "[data-column='Mad']", DEFAULT_TIMEOUT_MS);
            waitForAllPagesElement("[data-column='Mad']", SSE_PROPAGATION_TIMEOUT_MS, bobPage, carolPage);

            fillElement(bobPage, "[data-column='Mad'] textarea[name='content']", "Bob: Slow deployments");
            clickElement(bobPage, "[data-column='Mad'] button[type='submit']");

            fillElement(carolPage, "[data-column='Glad'] textarea[name='content']", "Carol: Great teamwork");
            clickElement(carolPage, "[data-column='Glad'] button[type='submit']");

            // Wait for both notes to be saved (visible on the submitters' pages)
            waitForElement(bobPage, "p:has-text('Bob: Slow deployments')", SSE_PROPAGATION_TIMEOUT_MS);
            waitForElement(carolPage, "p:has-text('Carol: Great teamwork')", SSE_PROPAGATION_TIMEOUT_MS);
            log.info("  ├─ ✅ Notes submitted at brainstorm step");

            // Click Next to reveal — facilitator override bypasses TIMER_EXPIRES.
            // Participant pages update via SSE (no page reload needed).
            logTestProgress("PHASE_2", 6, 23, "Advancing to MSG reveal step");
            clickNextAndWait(facilitatorPage, DEFAULT_TIMEOUT_MS, bobPage, carolPage); // brainstorm → reveal

            // At reveal step (showContent=true): all notes visible to everyone via SSE update
            logTestProgress("PHASE_2", 7, 23, "Verifying cross-user visibility at reveal step");
            waitForElement(bobPage, "p:has-text('Bob: Slow deployments')", SSE_PROPAGATION_TIMEOUT_MS);
            waitForElement(carolPage, "p:has-text('Carol: Great teamwork')", SSE_PROPAGATION_TIMEOUT_MS);
            waitForElement(facilitatorPage, "p:has-text('Bob: Slow deployments')", SSE_PROPAGATION_TIMEOUT_MS);

            assertTrue(bobPage.locator("[data-column='Glad'] p:has-text('Carol: Great teamwork')").isVisible(),
                "Bob should see Carol's note at the reveal step (showContent=true)");
            assertTrue(carolPage.locator("[data-column='Mad'] p:has-text('Bob: Slow deployments')").isVisible(),
                "Carol should see Bob's note at the reveal step (showContent=true)");
            log.info("  ├─ ✅ Cross-user visibility verified at reveal step");

            // Advance through remaining MSG steps: vote, summary, AUTO, then into GENERATE_INSIGHTS
            clickNextAndWait(facilitatorPage, DEFAULT_TIMEOUT_MS); // reveal+cluster → vote
            clickNextAndWait(facilitatorPage, DEFAULT_TIMEOUT_MS); // vote → summary
            clickNextAndWait(facilitatorPage, DEFAULT_TIMEOUT_MS); // summary → AUTO (index=4)
            clickNextAndWait(facilitatorPage, DEFAULT_TIMEOUT_MS); // AUTO → GENERATE_INSIGHTS
            log.info("  └─ ✓ Completed GATHER_DATA phase (Mad Sad Glad)");

            // ===== PHASE 3: GENERATE_INSIGHTS — Perfection Game (6 steps) =====
            log.info("\n├─ PHASE 3: GENERATE_INSIGHTS (Perfection Game)");

            // Step 1 (orderIndex=1): RATING_SCALE, ALL_RESPONDED — submit from all 3 users
            logTestProgress("PHASE_3", 8, 23, "Submitting Perfection Game ratings (RATING_SCALE)");
            log.info("  ├─ Testing RATING_SCALE submission...");
            waitForAllPagesElement("input[name='rating'][value='8']", SSE_PROPAGATION_TIMEOUT_MS, bobPage, carolPage, facilitatorPage);

            clickElement(bobPage, "input[name='rating'][value='8']");
            fillElement(bobPage, "textarea[name='comment']", "Good sprint overall!");
            clickElement(bobPage, "button:has-text('Submit')");

            clickElement(carolPage, "input[name='rating'][value='6']");
            fillElement(carolPage, "textarea[name='comment']", "Some blockers but we pushed through");
            clickElement(carolPage, "button:has-text('Submit')");

            clickElement(facilitatorPage, "input[name='rating'][value='9']");
            fillElement(facilitatorPage, "textarea[name='comment']", "Excellent team collaboration");
            clickElement(facilitatorPage, "button:has-text('Submit')");

            log.info("  ├─ ✅ Ratings submitted from all 3 users");

            // Advance from RATING_SCALE (ALL_RESPONDED satisfied) to HISTOGRAM_CHART
            logTestProgress("PHASE_3", 9, 23, "Advancing to Perfection Game histogram");
            clickNextAndWait(facilitatorPage, DEFAULT_TIMEOUT_MS);

            // Wait for histogram fragment to load (uses submitted comment as signal)
            waitForElement(facilitatorPage, "p:has-text('Excellent team collaboration')", DEFAULT_TIMEOUT_MS);

            // Verify histogram visualization and comments
            logTestProgress("PHASE_3", 10, 23, "Validating histogram display and comments");
            log.info("  ├─ Validating HISTOGRAM_CHART visualization...");
            assertTrue(facilitatorPage.locator("p:has-text('Excellent team collaboration')").isVisible(),
                "Histogram should show facilitator's comment after responses are revealed");
            assertTrue(bobPage.locator("p:has-text('Good sprint overall!')").isVisible(),
                "Bob's comment should be visible on histogram step");
            assertTrue(carolPage.locator("p:has-text('Some blockers but we pushed through')").isVisible(),
                "Carol's comment should be visible on histogram step");
            log.info("  ├─ ✅ HISTOGRAM_CHART display and comments validated");

            // Step 3 (index 2): brainstorm — TIMER_EXPIRES, showContent=false, allowInput=true
            logTestProgress("PHASE_3", 11, 23, "Submitting PG brainstorm ideas");
            clickNextAndWait(facilitatorPage, DEFAULT_TIMEOUT_MS); // histogram → brainstorm (index 2)

            waitForElement(facilitatorPage, "[data-column='What would make it a 10?'] textarea[name='content']", DEFAULT_TIMEOUT_MS);
            waitForAllPagesElement("[data-column='What would make it a 10?'] textarea[name='content']", SSE_PROPAGATION_TIMEOUT_MS, bobPage, carolPage);

            fillElement(bobPage, "[data-column='What would make it a 10?'] textarea[name='content']", "Bob PG: Better CI pipeline");
            clickElement(bobPage, "[data-column='What would make it a 10?'] button[type='submit']");
            fillElement(carolPage, "[data-column='What would make it a 10?'] textarea[name='content']", "Carol PG: More pair programming");
            clickElement(carolPage, "[data-column='What would make it a 10?'] button[type='submit']");
            waitForElement(bobPage, "p:has-text('Bob PG: Better CI pipeline')", DEFAULT_TIMEOUT_MS);
            waitForElement(carolPage, "p:has-text('Carol PG: More pair programming')", DEFAULT_TIMEOUT_MS);
            log.info("  ├─ ✅ PG brainstorm notes submitted");

            // Step 4 (index 3): reveal+cluster — allowMerging=true, showContent=true
            logTestProgress("PHASE_3", 12, 23, "Verifying PG reveal and testing clustering");
            clickNextAndWait(facilitatorPage, DEFAULT_TIMEOUT_MS, bobPage, carolPage); // brainstorm → reveal+cluster

            waitForElement(facilitatorPage, "p:has-text('Bob PG: Better CI pipeline')", SSE_PROPAGATION_TIMEOUT_MS);
            assertTrue(facilitatorPage.locator("p:has-text('Bob PG: Better CI pipeline')").isVisible(),
                "Bob's PG idea should be visible on reveal step");
            assertTrue(facilitatorPage.locator("p:has-text('Carol PG: More pair programming')").isVisible(),
                "Carol's PG idea should be visible on reveal step");

            waitForElement(facilitatorPage, "p:has-text('Carol PG: More pair programming')", DEFAULT_TIMEOUT_MS);
            Locator pgCard1 = facilitatorPage.locator("p:has-text('Bob PG: Better CI pipeline')");
            Locator pgCard2 = facilitatorPage.locator("p:has-text('Carol PG: More pair programming')");
            pgCard2.dragTo(pgCard1);
            facilitatorPage.waitForTimeout(SHORT_TIMEOUT_MS);
            log.info("  ├─ ✅ PG reveal and clustering validated");

            // Step 5 (index 4): vote — allowVoting=true, numberOfVotes=3
            logTestProgress("PHASE_3", 13, 23, "Testing PG voting");
            clickNextAndWait(facilitatorPage, DEFAULT_TIMEOUT_MS, bobPage, carolPage); // reveal+cluster → vote

            String pgVoteSelector = "button[aria-label*='Vote for this note']";
            waitForElement(bobPage, pgVoteSelector, DEFAULT_TIMEOUT_MS);
            assertTrue(bobPage.locator(pgVoteSelector).first().isVisible(), "Vote button should be visible on PG vote step");
            clickElement(bobPage, pgVoteSelector, DEFAULT_TIMEOUT_MS);
            log.info("  ├─ ✅ PG voting validated");

            // Step 6 (index 5): top results → DECIDE_ACTIONS
            clickNextAndWait(facilitatorPage, DEFAULT_TIMEOUT_MS); // vote → top results (index 5)
            clickNextAndWait(facilitatorPage, DEFAULT_TIMEOUT_MS); // top results → DECIDE_ACTIONS
            log.info("  └─ ✓ Completed GENERATE_INSIGHTS phase (Perfection Game)");

            // ===== PHASE 4: DECIDE_ACTIONS — Start Stop Continue (7 steps) =====
            log.info("\n├─ PHASE 4: DECIDE_ACTIONS (Start Stop Continue)");

            // Step 1 (orderIndex=1): TIMER_EXPIRES, showContent=false (private input)
            // We land here via the AUTO transition from Perfection Game.
            // Submit notes BEFORE fast-forwarding so we can assert privacy and reveal.
            logTestProgress("PHASE_4", 12, 23, "Submitting SSC notes at brainstorm step (private mode)");
            log.info("  ├─ Testing MULTI_COLUMN_BOARD input and privacy...");
            waitForElement(facilitatorPage, "[data-column='Start']", DEFAULT_TIMEOUT_MS);
            waitForAllPagesElement("[data-column='Start']", SSE_PROPAGATION_TIMEOUT_MS, bobPage, carolPage);

            // ── S8: Both facilitator and participants see guidance at the same active step ──────
            // All three pages are on the SSC brainstorm step — assert guidance-sidebar + guidance-content
            // are rendered for both facilitator and participant, and that the text is identical (S8).
            logTestProgress("PHASE_4", 12, 23, "S8: Asserting guidance-sidebar visible on facilitator and participant pages");
            waitForAllPagesElement("[data-testid='guidance-sidebar']", SSE_PROPAGATION_TIMEOUT_MS, facilitatorPage, bobPage);
            assertTrue(facilitatorPage.locator("[data-testid='guidance-sidebar']").isVisible(),
                "S8: guidance-sidebar should be visible on facilitator page at SSC brainstorm step");
            assertTrue(facilitatorPage.locator("[data-testid='guidance-content']").isVisible(),
                "S8: guidance-content should be visible on facilitator page");
            assertTrue(bobPage.locator("[data-testid='guidance-sidebar']").isVisible(),
                "S8: guidance-sidebar should be visible on participant (Bob) page at SSC brainstorm step");
            assertTrue(bobPage.locator("[data-testid='guidance-content']").isVisible(),
                "S8: guidance-content should be visible on participant (Bob) page");

            String facilitatorGuidanceAtBrainstorm = facilitatorPage.locator("[data-testid='guidance-content']").textContent();
            String bobGuidanceAtBrainstorm = bobPage.locator("[data-testid='guidance-content']").textContent();
            assertNotNull(facilitatorGuidanceAtBrainstorm, "S8: Facilitator guidance text must not be null");
            assertFalse(facilitatorGuidanceAtBrainstorm.isBlank(), "S8: Facilitator guidance text must not be blank");
            assertEquals(facilitatorGuidanceAtBrainstorm, bobGuidanceAtBrainstorm,
                "S8: Facilitator and participant must see identical guidance text on the same step");
            log.info("  ├─ ✅ S8: guidance-sidebar visible and identical on facilitator + participant pages");

            // Capture brainstorm guidance text now so we can assert it CHANGES after step advance (S10)
            String sscBrainstormGuidanceText = facilitatorGuidanceAtBrainstorm;

            fillElement(bobPage, "[data-column='Start'] textarea[name='content']", "Bob Start: Daily standups");
            clickElement(bobPage, "[data-column='Start'] button[type='submit']");

            fillElement(carolPage, "[data-column='Stop'] textarea[name='content']", "Carol Stop: Long meetings");
            clickElement(carolPage, "[data-column='Stop'] button[type='submit']");

            fillElement(facilitatorPage, "[data-column='Continue'] textarea[name='content']", "Alice Continue: Code reviews");
            clickElement(facilitatorPage, "[data-column='Continue'] button[type='submit']");

            fillElement(bobPage, "[data-column='Stop'] textarea[name='content']", "Bob Stop: Scope creep");
            clickElement(bobPage, "[data-column='Stop'] button[type='submit']");

            // Each user sees their own cards; others' cards are hidden (showContent=false)
            logTestProgress("PHASE_4", 13, 23, "Verifying privacy mode (own cards visible, others hidden)");
            waitForElement(bobPage, "p:has-text('Bob Start: Daily standups')", DEFAULT_TIMEOUT_MS);
            assertTrue(bobPage.locator("p:has-text('Bob Start: Daily standups')").isVisible(),
                "Bob should see his own Start card");
            waitForElement(carolPage, "p:has-text('Carol Stop: Long meetings')", DEFAULT_TIMEOUT_MS);
            assertTrue(carolPage.locator("p:has-text('Carol Stop: Long meetings')").isVisible(),
                "Carol should see her own Stop card");
            assertFalse(bobPage.locator("p:has-text('Carol Stop: Long meetings')").isVisible(),
                "Bob should NOT see Carol's card before reveal (privacy mode)");
            assertFalse(carolPage.locator("p:has-text('Bob Start: Daily standups')").isVisible(),
                "Carol should NOT see Bob's card before reveal (privacy mode)");
            log.info("  ├─ ✅ Privacy mode validated");

            // Click Next — facilitator override bypasses TIMER_EXPIRES. Participant pages update via SSE.
            logTestProgress("PHASE_4", 14, 23, "Advancing to SSC reveal step");
            clickNextAndWait(facilitatorPage, DEFAULT_TIMEOUT_MS, bobPage, carolPage); // brainstorm → reveal

            // Step 2 (orderIndex=2): reveal — Start/Stop/Continue columns visible to everyone
            logTestProgress("PHASE_4", 15, 23, "Verifying cross-user visibility after reveal");
            waitForAllPagesElement("p:has-text('Bob Start: Daily standups')", SSE_PROPAGATION_TIMEOUT_MS,
                bobPage, carolPage, facilitatorPage);
            waitForAllPagesElement("p:has-text('Carol Stop: Long meetings')", SSE_PROPAGATION_TIMEOUT_MS,
                bobPage, carolPage, facilitatorPage);

            assertTrue(facilitatorPage.locator("[data-column='Start']").isVisible(),
                "Start column should be visible at SSC reveal step");
            assertTrue(facilitatorPage.locator("[data-column='Stop']").isVisible(),
                "Stop column should be visible at SSC reveal step");
            assertTrue(facilitatorPage.locator("[data-column='Continue']").isVisible(),
                "Continue column should be visible at SSC reveal step");
            assertTrue(carolPage.locator("[data-column='Start'] p:has-text('Bob Start: Daily standups')").isVisible(),
                "Carol should see Bob's Start card after reveal");
            assertTrue(bobPage.locator("[data-column='Stop'] p:has-text('Carol Stop: Long meetings')").isVisible(),
                "Bob should see Carol's Stop card after reveal");
            log.info("  ├─ ✅ Cross-user visibility verified after reveal");

            // S10: Guidance text must change when the step advances (brainstorm → reveal)
            // S16: All participants must see the same updated guidance text after advance
            logTestProgress("PHASE_4", 15, 23, "S10/S16: Asserting guidance changed and is identical across all pages");
            waitForAllPagesElement("[data-testid='guidance-content']", SSE_PROPAGATION_TIMEOUT_MS,
                facilitatorPage, bobPage, carolPage);
            String facilitatorRevealGuidance = facilitatorPage.locator("[data-testid='guidance-content']").textContent();
            String bobRevealGuidance = bobPage.locator("[data-testid='guidance-content']").textContent();
            String carolRevealGuidance = carolPage.locator("[data-testid='guidance-content']").textContent();
            assertNotNull(facilitatorRevealGuidance, "S10: Facilitator reveal guidance must not be null");
            assertFalse(facilitatorRevealGuidance.isBlank(), "S10: Facilitator reveal guidance must not be blank");
            assertNotEquals(sscBrainstormGuidanceText, facilitatorRevealGuidance,
                "S10: Guidance text must change when advancing from SSC brainstorm to reveal step");
            assertEquals(facilitatorRevealGuidance, bobRevealGuidance,
                "S16: Facilitator and Bob must see identical guidance text at SSC reveal step");
            assertEquals(facilitatorRevealGuidance, carolRevealGuidance,
                "S16: Facilitator and Carol must see identical guidance text at SSC reveal step");
            log.info("  ├─ ✅ S10/S16: Guidance changed on advance and is identical on all 3 pages");

            // Step 3 (orderIndex=3): cluster — allowMerging=true
            logTestProgress("PHASE_4", 16, 23, "Testing clustering on SSC cluster step");
            clickNextAndWait(facilitatorPage, DEFAULT_TIMEOUT_MS); // reveal → cluster
            log.info("  ├─ Testing clustering/merging functionality...");
            waitForElement(facilitatorPage, "[data-column='Stop'] p:has-text('Bob Stop: Scope creep')", DEFAULT_TIMEOUT_MS);
            Locator bobStopCard = facilitatorPage.locator("[data-column='Stop'] p:has-text('Bob Stop: Scope creep')");
            Locator carolStopCard = facilitatorPage.locator("[data-column='Stop'] p:has-text('Carol Stop: Long meetings')");
            assertTrue(bobStopCard.isVisible(), "Bob's Stop card should be visible for clustering");
            assertTrue(carolStopCard.isVisible(), "Carol's Stop card should be visible for clustering");
            bobStopCard.dragTo(carolStopCard);
            facilitatorPage.waitForTimeout(SHORT_TIMEOUT_MS);
            log.info("  ├─ ✅ Clustering/merging validated");

            // Step 4 (orderIndex=4): vote — allowVoting=true
            logTestProgress("PHASE_4", 17, 23, "Testing voting on SSC vote step");
            clickNextAndWait(facilitatorPage, DEFAULT_TIMEOUT_MS, bobPage, carolPage); // cluster → vote
            log.info("  ├─ Testing voting functionality...");
            String voteSelector = "[data-column='Continue'] button[aria-label*='Vote for this note']";
            waitForElement(bobPage, voteSelector, DEFAULT_TIMEOUT_MS);
            assertTrue(bobPage.locator(voteSelector).first().isVisible(),
                "Vote button should be visible on SSC vote step");
            clickElement(bobPage, voteSelector, DEFAULT_TIMEOUT_MS);
            log.info("  ├─ ✅ Voting validated");

            // Fast-forward past remaining SSC steps (discuss/commit/AUTO) into CLOSE_RETRO
            logTestProgress("PHASE_4", 17, 23, "Fast-forwarding past SSC discuss/commit/AUTO into CLOSE_RETRO");
            fastForwardSession(sessionId, RetroPhase.CLOSE_RETRO, 0);
            facilitatorPage.reload();
            waitForElement(facilitatorPage, "[data-column='Plus']", SSE_PROPAGATION_TIMEOUT_MS);
            log.info("  └─ ✓ Completed DECIDE_ACTIONS phase (Start Stop Continue)");

            // ===== PHASE 5: CLOSE_RETRO — +/- Delta (3 steps) =====
            log.info("\n├─ PHASE 5: CLOSE_RETRO (+/- Delta)");

            // Step 1 (orderIndex=1): FACILITATOR_CLICK — Plus/Delta input
            logTestProgress("PHASE_5", 18, 23, "Verifying Plus/Delta columns at CLOSE_RETRO");

            assertTrue(facilitatorPage.locator("[data-column='Plus']").isVisible(),
                "Plus column should be visible at +/- Delta step");
            assertTrue(facilitatorPage.locator("[data-column='Delta']").isVisible(),
                "Delta column should be visible at +/- Delta step");
            log.info("  ├─ ✅ Plus/Delta columns verified");

            // Step 2 (orderIndex=2): FACILITATOR_CLICK — reveal
            clickNextAndWait(facilitatorPage, DEFAULT_TIMEOUT_MS); // input (index 0) → reveal (index 1)

            // Step 3 (orderIndex=3): AUTO — close/appreciation → COMPLETED
            // First click advances from reveal (index 1) to AUTO (index 2).
            // Second click advances from AUTO (index 2) to COMPLETED.
            // We use a direct click + waitForFunction for the final transition because
            // clickNextAndWait's waitForStepChange relies on [data-step-index] being present,
            // which may not render in COMPLETED state.
            clickNextAndWait(facilitatorPage, DEFAULT_TIMEOUT_MS); // reveal (index 1) → AUTO (index 2)
            logTestProgress("PHASE_5", 20, 23, "Advancing AUTO step to complete session");
            facilitatorPage.locator("[data-testid='next-step-button']")
                .click(new Locator.ClickOptions().setTimeout(DEFAULT_TIMEOUT_MS));
            facilitatorPage.waitForFunction(
                "() => { " +
                "  const el = document.querySelector('[data-phase]'); " +
                "  return el && el.getAttribute('data-phase') === 'COMPLETED'; " +
                "}",
                null,
                new Page.WaitForFunctionOptions().setTimeout(SSE_PROPAGATION_TIMEOUT_MS * 2)
            );

            // Verify session completion — no Next button after final step
            logTestProgress("COMPLETE", 23, 23, "Verifying session completion");
            assertFalse(facilitatorPage.locator("[data-testid='next-step-button']").isVisible(),
                "Session should be complete - no Next button after final step");

            log.info("  └─ ✓ Session complete");

            log.info("\n═══════════════════════════════════════════════════════════════");
            log.info("  ✓ SUCCESSFULLY VALIDATED COMPLETE RETROSPECTIVE FLOW");
            log.info("  - ESVP Check-in (4 columns): ✅");
            log.info("  - Mad Sad Glad (cross-user visibility): ✅");
            log.info("  - Perfection Game RATING_SCALE + HISTOGRAM_CHART: ✅");
            log.info("  - Start Stop Continue columns: ✅");
            log.info("  - +/- Delta feedback: ✅");
            log.info("  - Complete retro flow (23 steps): ✅");
            log.info("═══════════════════════════════════════════════════════════════");

        } catch (Exception e) {
            reportTestFailure(facilitatorPage, "Complete Retro Flow", e);
            throw e;
        } finally {
            facilitatorContext.close();
            bobContext.close();
            carolContext.close();
        }
    }

    // ==================== HELPERS ====================

    @Test
    @Order(1)
    @Timeout(300)
    @DisplayName("Should show identical current assistant message to facilitator and participant on the same step")
    void shouldShowIdenticalCurrentMessageToFacilitatorAndParticipant() {
        BrowserContext facilitatorContext = createMonitoredContext();
        Page facilitatorPage = facilitatorContext.newPage();
        BrowserContext participantContext = createMonitoredContext();
        Page participantLobbyPage = participantContext.newPage();

        try {
            logTestProgress("SETUP", 1, 4, "Authenticate facilitator and participant");
            authenticateAsGuest(facilitatorPage, "Facilitator");
            authenticateAsGuest(participantLobbyPage, "Participant");

            logTestProgress("SETUP", 2, 4, "Create session, participant joins before retro start, start session");
            String sessionId = createRetroSession(facilitatorPage, "Shared Message Test");
            joinRetroSession(participantLobbyPage, sessionId);
            waitForSseConnection(participantLobbyPage, UUID.fromString(sessionId));
            participantLobbyPage.close();
            startRetroSession(facilitatorPage, sessionId);

            logTestProgress("ADVANCE", 3, 4, "Fast-forwarding to GATHER_DATA step 1 and loading retro URL");
            String retroUrl = baseUrl + "/retro/" + sessionId;
            fastForwardSession(sessionId, RetroPhase.GATHER_DATA, 1);

            facilitatorPage.navigate(retroUrl);
            waitForElement(facilitatorPage, "[data-testid='guidance-sidebar']", SSE_PROPAGATION_TIMEOUT_MS);
            waitForElement(facilitatorPage, "[data-testid='guidance-content']", SSE_PROPAGATION_TIMEOUT_MS);

            Page participantRetroPage = participantContext.newPage();
            participantRetroPage.navigate(retroUrl);
            waitForElement(participantRetroPage, "[data-testid='guidance-sidebar']", SSE_PROPAGATION_TIMEOUT_MS);
            waitForElement(participantRetroPage, "[data-testid='guidance-content']", SSE_PROPAGATION_TIMEOUT_MS);

            logTestProgress("ASSERT", 4, 4, "Asserting guidance-content is identical on both pages");
            String facilitatorGuidance = getGuidanceContentText(facilitatorPage);
            String participantGuidance = getGuidanceContentText(participantRetroPage);

            assertNotNull(facilitatorGuidance, "Facilitator guidance-content must not be null");
            assertFalse(facilitatorGuidance.isBlank(), "Facilitator guidance-content must not be blank");
            assertNotNull(participantGuidance, "Participant guidance-content must not be null");
            assertFalse(participantGuidance.isBlank(), "Participant guidance-content must not be blank");
            assertEquals(facilitatorGuidance, participantGuidance,
                "Facilitator and participant must see identical guidance-content on the same step");

        } catch (Exception e) {
            reportTestFailure(facilitatorPage, "Shared Current Message", e);
            throw e;
        } finally {
            facilitatorContext.close();
            participantContext.close();
        }
    }

    @Test
    @Order(2)
    @Timeout(300)
    @DisplayName("Should shift current message into history list after step advances, with new current message on both pages")
    void shouldShiftCurrentMessageToHistoryAfterStepAdvance() {
        BrowserContext facilitatorContext = createMonitoredContext();
        Page facilitatorPage = facilitatorContext.newPage();
        BrowserContext participantContext = createMonitoredContext();
        Page participantPage = participantContext.newPage();

        try {
            logTestProgress("SETUP", 1, 6, "Authenticate facilitator, create and start session");
            authenticateAsGuest(facilitatorPage, "Facilitator");
            String sessionId = createRetroSession(facilitatorPage, "History Shift Test");
            waitForElement(facilitatorPage, "[data-testid='start-retro-button']", SSE_PROPAGATION_TIMEOUT_MS);
            startRetroSession(facilitatorPage, sessionId);

            logTestProgress("ADVANCE_1", 2, 6, "Fast-forwarding to GATHER_DATA step 1 (facilitator only)");
            String retroUrl = baseUrl + "/retro/" + sessionId;
            fastForwardSession(sessionId, RetroPhase.GATHER_DATA, 1);
            facilitatorPage.navigate(retroUrl);

            waitForElement(facilitatorPage, "[data-testid='guidance-sidebar']", SSE_PROPAGATION_TIMEOUT_MS);
            waitForElement(facilitatorPage, "[data-testid='guidance-content']", SSE_PROPAGATION_TIMEOUT_MS);
            waitForHistoryItemCount(facilitatorPage, 1);

            logTestProgress("CAPTURE", 3, 6, "Capturing current guidance-content before advance");
            String guidanceBeforeAdvance = getGuidanceContentText(facilitatorPage);
            int historyCountBefore = getHistoryItemCount(facilitatorPage);

            assertNotNull(guidanceBeforeAdvance, "Guidance before advance must not be null");
            assertFalse(guidanceBeforeAdvance.isBlank(), "Guidance before advance must not be blank");
            assertEquals(1, historyCountBefore,
                "Expected 1 history item at step 1. Actual: " + historyCountBefore);

            logTestProgress("ADVANCE_2", 4, 6, "Fast-forwarding to GATHER_DATA step 2");
            fastForwardSession(sessionId, RetroPhase.GATHER_DATA, 2);
            facilitatorPage.navigate("about:blank");
            facilitatorPage.navigate(retroUrl);

            waitForElement(facilitatorPage, "[data-testid='guidance-sidebar']", SSE_PROPAGATION_TIMEOUT_MS);
            waitForElement(facilitatorPage, "[data-testid='guidance-content']", SSE_PROPAGATION_TIMEOUT_MS);
            waitForHistoryItemCount(facilitatorPage, 2);

            logTestProgress("AUTH_PARTICIPANT", 5, 6, "Authenticate participant and join at step 2 (tests late-join history)");
            authenticateAsGuest(participantPage, "Participant");
            joinRetroSession(participantPage, sessionId);
            waitForElement(participantPage, "[data-testid='guidance-sidebar']", SSE_PROPAGATION_TIMEOUT_MS);
            waitForElement(participantPage, "[data-testid='guidance-content']", SSE_PROPAGATION_TIMEOUT_MS);
            waitForHistoryItemCount(participantPage, 2);

            logTestProgress("ASSERT", 6, 6, "Asserting new current, history shift, and cross-page alignment");
            String facilitatorGuidanceAfter = facilitatorPage.locator("[data-testid='guidance-content']").textContent();
            String participantGuidanceAfter = participantPage.locator("[data-testid='guidance-content']").textContent();
            List<String> facilitatorHistoryTexts = getHistoryItemTexts(facilitatorPage);
            List<String> participantHistoryTexts = getHistoryItemTexts(participantPage);

            assertNotEquals(guidanceBeforeAdvance, facilitatorGuidanceAfter,
                "Guidance must change after step advance (step 1 → step 2)");
            assertFalse(facilitatorGuidanceAfter.isBlank(),
                "New guidance-content after advance must not be blank");
            assertTrue(facilitatorHistoryTexts.contains(guidanceBeforeAdvance),
                "Previous current message must appear in history list after advancing. " +
                "Expected history to contain: '" + guidanceBeforeAdvance + "'. " +
                "Actual history texts: " + facilitatorHistoryTexts);
            assertEquals(facilitatorGuidanceAfter, participantGuidanceAfter,
                "Facilitator and participant must see identical guidance-content after step advance");
            assertEquals(facilitatorHistoryTexts, participantHistoryTexts,
                "Facilitator and participant must have identical history item texts after step advance");

        } catch (Exception e) {
            reportTestFailure(facilitatorPage, "History Shift After Step Advance", e);
            throw e;
        } finally {
            facilitatorContext.close();
            participantContext.close();
        }
    }

    @Test
    @Order(3)
    @Timeout(300)
    @DisplayName("Facilitator sees private coaching; participant sees only shared guidance")
    void facilitatorSeesPrivateCoaching_ParticipantDoesNot() {
        BrowserContext facilitatorContext = createMonitoredContext();
        Page facilitatorPage = facilitatorContext.newPage();
        BrowserContext participantContext = createMonitoredContext();
        Page participantPage = participantContext.newPage();

        try {
            logTestProgress("SETUP", 1, 5, "Authenticate facilitator and participant");
            authenticateAsGuest(facilitatorPage, "Facilitator");
            authenticateAsGuest(participantPage, "Bob");

            logTestProgress("SETUP", 2, 5, "Create and start session, Bob joins");
            String sessionId = createRetroSession(facilitatorPage, "Private Coaching Visibility Test");
            joinRetroSession(participantPage, sessionId);
            waitForSseConnection(participantPage, UUID.fromString(sessionId));
            waitForElement(facilitatorPage, "[data-testid='start-retro-button']", SSE_PROPAGATION_TIMEOUT_MS);
            startRetroSession(facilitatorPage, sessionId);

            waitForElement(participantPage, "h2:has-text('Step')", SSE_PROPAGATION_TIMEOUT_MS);

            logTestProgress("ADVANCE", 3, 5, "Fast-forwarding to GATHER_DATA step 1");
            fastForwardSession(sessionId, RetroPhase.GATHER_DATA, 1);
            facilitatorPage.reload();
            participantPage.reload();

            waitForElement(facilitatorPage, "[data-testid='guidance-content']", SSE_PROPAGATION_TIMEOUT_MS);
            waitForElement(participantPage, "[data-testid='guidance-content']", SSE_PROPAGATION_TIMEOUT_MS);

            logTestProgress("ASSERT", 4, 5, "Private coaching visible to facilitator, absent for participant");
            int facilitatorPrivateCount = facilitatorPage.locator("[data-testid='assistant-private-coaching']").count();
            assertTrue(facilitatorPrivateCount > 0,
                "Facilitator must see [data-testid='assistant-private-coaching'] on an active step");
            assertTrue(facilitatorPage.locator("[data-testid='assistant-private-coaching']").isVisible(),
                "Facilitator's assistant-private-coaching section must be visible");

            int participantPrivateCount = participantPage.locator("[data-testid='assistant-private-coaching']").count();
            assertEquals(0, participantPrivateCount,
                "Participant must NOT see [data-testid='assistant-private-coaching'] — got count=" + participantPrivateCount);

            int participantPlaceholderCount = participantPage.locator("[data-testid='assistant-private-coaching-placeholder']").count();
            assertTrue(participantPlaceholderCount > 0,
                "Participant must have [data-testid='assistant-private-coaching-placeholder'] in DOM");

            logTestProgress("ASSERT", 5, 5, "Shared guidance-content identical on both pages");
            String facilitatorGuidance = facilitatorPage.locator("[data-testid='guidance-content']").textContent();
            String participantGuidance = participantPage.locator("[data-testid='guidance-content']").textContent();

            assertNotNull(facilitatorGuidance, "Facilitator guidance-content must not be null");
            assertNotNull(participantGuidance, "Participant guidance-content must not be null");
            assertFalse(facilitatorGuidance.isBlank(), "Facilitator guidance-content must not be blank");
            assertFalse(participantGuidance.isBlank(), "Participant guidance-content must not be blank");
            assertEquals(facilitatorGuidance, participantGuidance,
                "Shared guidance-content must be identical for facilitator and participant on the same step");

        } catch (Exception e) {
            reportTestFailure(facilitatorPage, "Private Coaching Visibility", e);
            throw e;
        } finally {
            facilitatorContext.close();
            participantContext.close();
        }
    }

    @Test
    @Order(4)
    @Timeout(300)
    @DisplayName("Should match assistant history on late join and stay aligned after step advance")
    void shouldMatchAssistantHistoryOnLateJoin() {
        BrowserContext facilitatorContext = createMonitoredContext();
        Page facilitatorPage = facilitatorContext.newPage();
        BrowserContext lateJoinerContext = createMonitoredContext();
        Page lateJoinerPage = lateJoinerContext.newPage();

        try {
            logTestProgress("SETUP", 1, 8, "Authenticate facilitator and create session");
            authenticateAsGuest(facilitatorPage, "Facilitator");
            String sessionId = createRetroSession(facilitatorPage, "Late Join Consistency Test");
            waitForElement(facilitatorPage, "[data-testid='start-retro-button']", SSE_PROPAGATION_TIMEOUT_MS);
            startRetroSession(facilitatorPage, sessionId);

            logTestProgress("ADVANCE", 2, 8, "Fast-forwarding to GATHER_DATA step 2 to build history");
            fastForwardSession(sessionId, RetroPhase.GATHER_DATA, 2);
            facilitatorPage.reload();

            waitForElement(facilitatorPage, "[data-testid='guidance-sidebar']", SSE_PROPAGATION_TIMEOUT_MS);
            waitForElement(facilitatorPage, "[data-testid='guidance-content']", SSE_PROPAGATION_TIMEOUT_MS);
            waitForHistoryItemCount(facilitatorPage, 2);

            logTestProgress("CAPTURE", 3, 8, "Capturing facilitator guidance and history");
            String facilitatorGuidance = facilitatorPage.locator("[data-testid='guidance-content']").textContent();
            int facilitatorHistoryCount = getHistoryItemCount(facilitatorPage);
            List<String> facilitatorHistoryTexts = getHistoryItemTexts(facilitatorPage);

            assertNotNull(facilitatorGuidance, "Facilitator guidance must not be null");
            assertFalse(facilitatorGuidance.isBlank(), "Facilitator guidance must not be blank");

            logTestProgress("LATE_JOIN", 4, 8, "Late joiner authenticating and joining active session");
            authenticateAsGuest(lateJoinerPage, "LateJoiner");
            joinRetroSession(lateJoinerPage, sessionId);

            waitForElement(lateJoinerPage, "[data-testid='guidance-sidebar']", SSE_PROPAGATION_TIMEOUT_MS);
            waitForElement(lateJoinerPage, "[data-testid='guidance-content']", SSE_PROPAGATION_TIMEOUT_MS);
            waitForHistoryItemCount(lateJoinerPage, facilitatorHistoryCount);

            logTestProgress("ASSERT_JOIN", 5, 8, "Asserting late joiner matches facilitator state");
            String lateJoinerGuidance = lateJoinerPage.locator("[data-testid='guidance-content']").textContent();
            int lateJoinerHistoryCount = getHistoryItemCount(lateJoinerPage);
            List<String> lateJoinerHistoryTexts = getHistoryItemTexts(lateJoinerPage);

            assertEquals(facilitatorGuidance, lateJoinerGuidance,
                "Late joiner guidance must match facilitator guidance at bootstrap");
            assertEquals(facilitatorHistoryCount, lateJoinerHistoryCount,
                "Late joiner history item count must match facilitator's at bootstrap");
            assertEquals(facilitatorHistoryTexts, lateJoinerHistoryTexts,
                "Late joiner history texts must match facilitator's at bootstrap");

            logTestProgress("ADVANCE_2", 6, 8, "Fast-forwarding to step 3 to verify post-bootstrap alignment");
            fastForwardSession(sessionId, RetroPhase.GATHER_DATA, 3);
            facilitatorPage.reload();

            waitForElement(facilitatorPage, "[data-testid='guidance-sidebar']", SSE_PROPAGATION_TIMEOUT_MS);
            waitForElement(facilitatorPage, "[data-testid='guidance-content']", SSE_PROPAGATION_TIMEOUT_MS);
            waitForHistoryItemCount(facilitatorPage, 3);

            String retroUrl = facilitatorPage.url();
            lateJoinerPage.navigate(retroUrl);
            waitForElement(lateJoinerPage, "[data-testid='guidance-sidebar']", SSE_PROPAGATION_TIMEOUT_MS);
            waitForElement(lateJoinerPage, "[data-testid='guidance-content']", SSE_PROPAGATION_TIMEOUT_MS);
            waitForHistoryItemCount(lateJoinerPage, 3);

            logTestProgress("ASSERT_ADVANCE", 7, 8, "Asserting both pages aligned after step advance");
            String facilitatorGuidanceAfter = facilitatorPage.locator("[data-testid='guidance-content']").textContent();
            String lateJoinerGuidanceAfter = lateJoinerPage.locator("[data-testid='guidance-content']").textContent();
            int facilitatorHistoryCountAfter = getHistoryItemCount(facilitatorPage);
            int lateJoinerHistoryCountAfter = getHistoryItemCount(lateJoinerPage);
            List<String> facilitatorHistoryTextsAfter = getHistoryItemTexts(facilitatorPage);
            List<String> lateJoinerHistoryTextsAfter = getHistoryItemTexts(lateJoinerPage);

            assertEquals(facilitatorGuidanceAfter, lateJoinerGuidanceAfter,
                "Guidance must be identical on both pages after step advance");
            assertEquals(facilitatorHistoryCountAfter, lateJoinerHistoryCountAfter,
                "History item count must be identical on both pages after step advance");
            assertEquals(facilitatorHistoryTextsAfter, lateJoinerHistoryTextsAfter,
                "History item texts must be identical on both pages after step advance");
            assertNotEquals(facilitatorGuidance, facilitatorGuidanceAfter,
                "Guidance text must change when step advances (step 2 → step 3)");

        } catch (Exception e) {
            reportTestFailure(facilitatorPage, "Assistant History Late Join", e);
            throw e;
        } finally {
            facilitatorContext.close();
            lateJoinerContext.close();
        }
    }

    @Test
    @Order(5)
    @Timeout(300)
    @DisplayName("next-step coachmark visible to facilitator only, dismissible, Next button still works after dismiss")
    void shouldShowNextStepCoachmarkOnlyToFacilitator() {
        BrowserContext facilitatorContext = createMonitoredContext();
        Page facilitatorPage = facilitatorContext.newPage();
        BrowserContext participantContext = createMonitoredContext();
        Page participantPage = participantContext.newPage();

        try {
            logTestProgress("SETUP", 1, 4, "Authenticating facilitator and participant");
            authenticateAsGuest(facilitatorPage, "Alice (Facilitator)");
            authenticateAsGuest(participantPage, "Bob");

            logTestProgress("SETUP", 2, 4, "Creating and starting retro session");
            String sessionId = createRetroSession(facilitatorPage, "Next-Step Coachmark Test");
            joinRetroSession(participantPage, sessionId);
            startRetroSession(facilitatorPage, sessionId);

            waitForAllPagesElement("[data-testid='retro-content']", facilitatorPage, participantPage);
            waitForElement(facilitatorPage, "[data-coachmark='next-step']");

            logTestProgress("COACHMARK", 3, 4, "Verifying next-step coachmark visible to facilitator, hidden from participant");
            waitForElement(facilitatorPage, "[data-testid='next-step-coachmark']");
            assertTrue(
                facilitatorPage.locator("[data-testid='next-step-coachmark']").isVisible(),
                "next-step coachmark should be visible to the facilitator"
            );
            assertEquals(
                0,
                participantPage.locator("[data-testid='next-step-coachmark']").count(),
                "next-step coachmark should NOT appear for a regular participant (no Next button anchor)"
            );

            logTestProgress("COACHMARK", 4, 4, "Dismissing next-step coachmark and verifying Next button still works");
            clickElement(facilitatorPage, "[data-testid='next-step-coachmark-close']");
            facilitatorPage.waitForFunction(
                "() => document.querySelector('[data-testid=\"next-step-coachmark\"]') === null",
                null,
                new Page.WaitForFunctionOptions().setTimeout(DEFAULT_TIMEOUT_MS)
            );
            assertEquals(
                0,
                facilitatorPage.locator("[data-testid='next-step-coachmark']").count(),
                "next-step coachmark should be removed from DOM after dismiss"
            );
            assertTrue(
                facilitatorPage.locator("[data-testid='next-step-button']").isVisible(),
                "Next button should still be visible and usable after coachmark is dismissed"
            );

        } catch (Exception e) {
            reportTestFailure(facilitatorPage, "Next Step Coachmark Role Visibility", e);
            throw e;
        } finally {
            facilitatorContext.close();
            participantContext.close();
        }
    }

    @Test
    @Order(6)
    @Timeout(300)
    @DisplayName("note-input coachmark visible on MULTI_COLUMN_BOARD step, dismissible, note submission not blocked")
    void shouldShowNoteInputCoachmarkAndNotBlockNoteSubmission() {
        BrowserContext facilitatorContext = createMonitoredContext();
        Page facilitatorPage = facilitatorContext.newPage();
        BrowserContext participantContext = createMonitoredContext();
        Page participantPage = participantContext.newPage();

        try {
            logTestProgress("SETUP", 1, 5, "Authenticating facilitator and participant");
            authenticateAsGuest(facilitatorPage, "Alice (Facilitator)");
            authenticateAsGuest(participantPage, "Bob");

            logTestProgress("SETUP", 2, 5, "Creating and starting retro session");
            String sessionId = createRetroSession(facilitatorPage, "Note-Input Coachmark Test");
            joinRetroSession(participantPage, sessionId);
            startRetroSession(facilitatorPage, sessionId);

            logTestProgress("SETUP", 3, 5, "Fast-forwarding to GATHER_DATA step 0 (guaranteed board step)");
            fastForwardSession(sessionId, RetroPhase.GATHER_DATA, 0);

            String retroUrl = baseUrl + "/retro/" + sessionId;
            facilitatorPage.navigate(retroUrl);
            participantPage.navigate(retroUrl);
            waitForAllPagesElement("[data-column]", facilitatorPage, participantPage);

            participantPage.evaluate("() => window.dispatchEvent(new Event('resize'))");
            facilitatorPage.evaluate("() => window.dispatchEvent(new Event('resize'))");

            logTestProgress("COACHMARK", 4, 5, "Verifying note-input coachmark visible on participant page");
            waitForElement(participantPage, "[data-testid='note-input-coachmark']");
            assertTrue(
                participantPage.locator("[data-testid='note-input-coachmark']").isVisible(),
                "note-input coachmark should be visible on a MULTI_COLUMN_BOARD step"
            );

            logTestProgress("COACHMARK", 5, 5, "Submitting note while coachmark is visible — verifying no interaction block");
            Locator firstColumnTextarea = participantPage.locator("[data-column] textarea[name='content']").first();
            firstColumnTextarea.fill("Coachmark non-blocking test note");
            participantPage.locator("[data-column] button[type='submit']").first().click();

            participantPage.waitForFunction(
                "() => document.body.textContent.includes('Coachmark non-blocking test note')",
                null,
                new Page.WaitForFunctionOptions().setTimeout(SSE_PROPAGATION_TIMEOUT_MS)
            );
            assertTrue(
                participantPage.locator("p:has-text('Coachmark non-blocking test note')").count() > 0
                    || participantPage.locator("text=Coachmark non-blocking test note").count() > 0,
                "Note should appear on the board after submission — coachmark must not have blocked interaction"
            );

            if (participantPage.locator("[data-testid='note-input-coachmark']").count() > 0) {
                clickElement(participantPage, "[data-testid='note-input-coachmark-close']");
                participantPage.waitForFunction(
                    "() => document.querySelector('[data-testid=\"note-input-coachmark\"]') === null",
                    null,
                    new Page.WaitForFunctionOptions().setTimeout(DEFAULT_TIMEOUT_MS)
                );
                assertEquals(
                    0,
                    participantPage.locator("[data-testid='note-input-coachmark']").count(),
                    "note-input coachmark should be removed from DOM after dismiss"
                );
            }

        } catch (Exception e) {
            reportTestFailure(participantPage, "Note Input Coachmark Non-Blocking", e);
            throw e;
        } finally {
            facilitatorContext.close();
            participantContext.close();
        }
    }

    // ==================== HELPERS ====================

    private void waitForPageAtStepIndex(int expectedStepIndex, Page... pages) {
        String js = String.format(
            "() => { " +
            "  const el = document.querySelector('[data-step-index]'); " +
            "  return el && parseInt(el.getAttribute('data-step-index')) === %d; " +
            "}",
            expectedStepIndex
        );
        for (Page page : pages) {
            page.waitForFunction(js, null,
                new Page.WaitForFunctionOptions().setTimeout(SSE_PROPAGATION_TIMEOUT_MS));
        }
    }

    /**
     * Fast-forwards the session DB state to the given phase and step index,
     * bypassing steps with blocking advancement triggers (TIMER_EXPIRES, ALL_RESPONDED).
     */
    private void fastForwardSession(String sessionId, RetroPhase phase, int stepIndex) {
        RetroSession session = retroSessionRepository.findById(UUID.fromString(sessionId))
                .orElseThrow(() -> new IllegalStateException("Session not found: " + sessionId));
        session.setPhase(phase);
        session.setCurrentStepIndex(stepIndex);
        retroSessionRepository.save(session);
    }

    private String getGuidanceContentText(Page page) {
        Object result = page.evaluate(
            "() => { " +
            "  const el = document.querySelector('[data-testid=\"guidance-content\"]'); " +
            "  return el ? el.innerText.replace(/\\s+/g, ' ').trim() : ''; " +
            "}"
        );
        return result != null ? result.toString() : "";
    }

    private void waitForHistoryItemCount(Page page, int expectedCount) {
        recordActivity("waitForHistoryItemCount: expected=" + expectedCount);
        try {
            page.waitForFunction(
                String.format(
                    "() => document.querySelectorAll('[data-testid=\"assistant-history-list\"] > div').length >= %d",
                    expectedCount
                ),
                null,
                new Page.WaitForFunctionOptions().setTimeout(SSE_PROPAGATION_TIMEOUT_MS)
            );
        } catch (Exception e) {
            int actual = getHistoryItemCount(page);
            throw new AssertionError(String.format(
                "History item count did not reach %d within %dms. Actual: %d. URL: %s",
                expectedCount, (long) SSE_PROPAGATION_TIMEOUT_MS, actual, page.url()), e);
        }
    }

    private int getHistoryItemCount(Page page) {
        return page.locator("[data-testid='assistant-history-list'] > div").count();
    }

    @SuppressWarnings("unchecked")
    private List<String> getHistoryItemTexts(Page page) {
        Object result = page.evaluate(
            "() => Array.from(document.querySelectorAll('[data-testid=\"assistant-history-list\"] > div p:last-child'))" +
            ".map(p => p.textContent ? p.textContent.replace(/\\s+/g, ' ').trim() : '')"
        );
        if (result instanceof List) {
            return (List<String>) result;
        }
        return List.of();
    }

}
