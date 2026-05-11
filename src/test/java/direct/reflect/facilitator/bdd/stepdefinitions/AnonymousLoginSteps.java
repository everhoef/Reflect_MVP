package direct.reflect.facilitator.bdd.stepdefinitions;

import com.microsoft.playwright.options.Cookie;
import direct.reflect.facilitator.bdd.support.context.RetroScenarioContext;
import direct.reflect.facilitator.bdd.support.drivers.RetroSessionDriver;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.cucumber.spring.ScenarioScope;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Assertions;

import java.util.List;
import java.util.UUID;

@ScenarioScope
@RequiredArgsConstructor
public class AnonymousLoginSteps {

    private final RetroSessionDriver retroSessionDriver;
    private final RetroScenarioContext context;

    @Given("I am a team member")
    public void iAmATeamMember() {
    }

    @Given("I have received a unique session link for a retrospective")
    public void iHaveReceivedAUniqueSessionLinkForARetrospective() {
        retroSessionDriver.createRetroAndGetLobbyUrl();
    }

    @When("I click on the session link")
    public void iClickOnTheSessionLink() {
        if (context.getSessionId() != null && !context.getSessionId().isBlank()) {
            retroSessionDriver.joinRetroAsGuest(context.getSessionId(), "Test Guest");
        } else {
            retroSessionDriver.authenticateAsGuest("Test Guest");
            retroSessionDriver.navigateToRetro(UUID.randomUUID().toString());
        }
    }

    @Then("I should be automatically logged into the session")
    public void iShouldBeAutomaticallyLoggedIntoTheSession() {
        retroSessionDriver.assertGuestAuthenticated();
    }

    @Then("I should not be prompted for username, password, or email")
    public void iShouldNotBePromptedForUsernamePasswordOrEmail() {
        retroSessionDriver.assertNoLoginPrompts();
    }

    @Then("I should see the retrospective interface immediately")
    public void iShouldSeeTheRetrospectiveInterfaceImmediately() {
        retroSessionDriver.assertRetroPageVisible();
    }

    @Given("I am participating in a retrospective session with pseudonym {string}")
    public void iAmParticipatingInARetrospectiveSessionWithPseudonym(String displayName) {
        retroSessionDriver.createRetroAndGetLobbyUrl();
        retroSessionDriver.advanceToPhase(2);
        retroSessionDriver.advanceFacilitatorUntilColumnBoardVisible();
        context.setCurrentPhaseNumber(2);
        context.setFacilitatorCookies(retroSessionDriver.captureCookies());
        retroSessionDriver.clearCookies();
        retroSessionDriver.joinRetroAsGuest(context.getSessionId(), displayName);
        retroSessionDriver.waitForColumnBoardVisible();
        context.setParticipantCookies(retroSessionDriver.captureCookies());
    }

    @When("I add a card, vote, or comment")
    public void iAddACardVoteOrComment() {
        String columnId = retroSessionDriver.findFirstColumnId();
        String noteContent = "BDD test note from " + System.currentTimeMillis();
        context.setLastNoteContent(noteContent);
        retroSessionDriver.addNoteAndWait(columnId, noteContent);
        retroSessionDriver.restoreCookies(context.getFacilitatorCookies());
        retroSessionDriver.reloadAndWait();
        retroSessionDriver.waitForColumnBoardVisible();
        retroSessionDriver.advanceOneStep();
    }

    @Then("all my contributions should display as coming from {string}")
    public void allMyContributionsShouldDisplayAsComingFrom(String displayName) {
        advanceToRevealStepAsFacilitator();
        rejoinAsParticipantAndAssertAuthor(displayName);
    }

    @Then("other participants should see my contributions attributed to {string}")
    public void otherParticipantsShouldSeeMyContributionsAttributedTo(String displayName) {
        retroSessionDriver.restoreCookies(context.getFacilitatorCookies());
        retroSessionDriver.reloadAndWait();
        retroSessionDriver.assertNoteShowsAuthor(displayName);
    }

    @Then("I should be able to identify my own contributions")
    public void iShouldBeAbleToIdentifyMyOwnContributions() {
        List<Cookie> participantCookies = context.getParticipantCookies();
        if (participantCookies == null) {
            throw new AssertionError("Participant cookies not captured — cannot verify own contributions.");
        }
        retroSessionDriver.restoreCookies(participantCookies);
        retroSessionDriver.reloadAndWait();
        retroSessionDriver.waitForColumnBoardVisible();
        retroSessionDriver.assertOwnNoteVisible(context.getLastNoteContent());
    }

    @Given("I was disconnected from a retrospective session")
    public void iWasDisconnectedFromARetrospectiveSession() {
        retroSessionDriver.createRetroAndGetLobbyUrl();
        retroSessionDriver.advanceToPhase(2);
        retroSessionDriver.advanceFacilitatorUntilColumnBoardVisible();
        context.setCurrentPhaseNumber(2);
        retroSessionDriver.clearCookies();
        retroSessionDriver.joinRetroAsGuest(context.getSessionId(), "Reconnect User");
        retroSessionDriver.waitForColumnBoardVisible();
        context.setParticipantCookies(retroSessionDriver.captureCookies());
        String columnId = retroSessionDriver.findFirstColumnId();
        String noteContent = "Note before disconnect " + System.currentTimeMillis();
        context.setLastNoteContent(noteContent);
        retroSessionDriver.addNoteAndWait(columnId, noteContent);
    }

    @Given("the session is still active")
    public void theSessionIsStillActive() {
    }

    @When("I rejoin via the unique session link")
    public void iRejoinViaTheUniqueSessionLink() {
        List<Cookie> participantCookies = context.getParticipantCookies();
        if (participantCookies == null) {
            throw new AssertionError("Participant cookies not captured for re-entry.");
        }
        retroSessionDriver.clearCookies();
        retroSessionDriver.restoreCookies(participantCookies);
        retroSessionDriver.navigateToRetro(context.getSessionId());
        retroSessionDriver.assertRetroPageVisible();
    }

    @Then("I should see the current phase and timer state")
    public void iShouldSeeTheCurrentPhaseAndTimerState() {
        int currentPhase = retroSessionDriver.detectCurrentPhaseNumber();
        Assertions.assertEquals(context.getCurrentPhaseNumber(), currentPhase,
            "Expected phase to be preserved after re-entry.");
    }

    @Then("I should see all contributions made during my absence")
    public void iShouldSeeAllContributionsMadeDuringMyAbsence() {
        retroSessionDriver.waitForColumnBoardVisible();
        retroSessionDriver.assertNoteVisible(context.getLastNoteContent());
    }

    @Then("I should be able to continue participating seamlessly")
    public void iShouldBeAbleToContinueParticipatingSeamlessly() {
        String columnId = retroSessionDriver.findFirstColumnId();
        String newNote = "Note after reconnect " + System.currentTimeMillis();
        retroSessionDriver.addNoteAndWait(columnId, newNote);
    }

    @Given("I have a session link for a retrospective")
    public void iHaveASessionLinkForARetrospective() {
    }

    @Given("the session has ended or the link is invalid")
    public void theSessionHasEndedOrTheLinkIsInvalid() {
    }

    @Then("I should see an error message indicating the session is unavailable")
    public void iShouldSeeAnErrorMessageIndicatingTheSessionIsUnavailable() {
        retroSessionDriver.assertErrorPage();
    }

    @Then("I should not be able to access the retrospective")
    public void iShouldNotBeAbleToAccessTheRetrospective() {
        retroSessionDriver.assertRetroContentNotVisible();
    }

    @Given("I am a team member with a session link")
    public void iAmATeamMemberWithASessionLink() {
        retroSessionDriver.createRetroAndGetLobbyUrl();
    }

    @When("I access the retrospective")
    public void iAccessTheRetrospective() {
        retroSessionDriver.clearCookies();
        retroSessionDriver.joinRetroAsGuest(context.getSessionId(), "Frictionless Guest");
    }

    @Then("I should not be required to create an account")
    public void iShouldNotBeRequiredToCreateAnAccount() {
        retroSessionDriver.assertPageLacksText("create an account");
        retroSessionDriver.assertPageLacksText("sign up");
        retroSessionDriver.assertPageLacksText("register");
    }

    @Then("I should not be required to remember a password")
    public void iShouldNotBeRequiredToRememberAPassword() {
        retroSessionDriver.assertNoLoginPrompts();
    }

    @Then("I should not be required to verify my email")
    public void iShouldNotBeRequiredToVerifyMyEmail() {
        retroSessionDriver.assertPageLacksText("verify your email");
        retroSessionDriver.assertPageLacksText("email verification");
    }

    @Then("I should not be required to complete a CAPTCHA")
    public void iShouldNotBeRequiredToCompleteACAPTCHA() {
        retroSessionDriver.assertPageLacksText("CAPTCHA");
        retroSessionDriver.assertPageLacksText("captcha");
        retroSessionDriver.assertPageLacksText("I'm not a robot");
    }

    @Then("I should not be required to provide any personal information")
    public void iShouldNotBeRequiredToProvideAnyPersonalInformation() {
        retroSessionDriver.assertNoPersonalInfoFields();
    }

    @Then("I should reach the retrospective interface in one click")
    public void iShouldReachTheRetrospectiveInterfaceInOneClick() {
        retroSessionDriver.assertRetroPageVisible();
    }

    private void advanceToRevealStepAsFacilitator() {
        List<Cookie> facilitatorCookies = context.getFacilitatorCookies();
        if (facilitatorCookies == null) {
            throw new AssertionError("Facilitator cookies not captured.");
        }
        retroSessionDriver.restoreCookies(facilitatorCookies);
        retroSessionDriver.reloadAndWait();
        retroSessionDriver.waitForColumnBoardVisible();
        retroSessionDriver.advanceOneStep();
    }

    private void rejoinAsParticipantAndAssertAuthor(String displayName) {
        List<Cookie> participantCookies = context.getParticipantCookies();
        if (participantCookies == null) {
            throw new AssertionError("Participant cookies not captured.");
        }
        retroSessionDriver.restoreCookies(participantCookies);
        retroSessionDriver.reloadAndWait();
        retroSessionDriver.waitForColumnBoardVisible();
        retroSessionDriver.assertNoteShowsAuthor(displayName);
    }
}
