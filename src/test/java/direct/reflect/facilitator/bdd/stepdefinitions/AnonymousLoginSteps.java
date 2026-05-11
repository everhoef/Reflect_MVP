package direct.reflect.facilitator.bdd.stepdefinitions;

import com.microsoft.playwright.options.Cookie;
import direct.reflect.facilitator.bdd.support.context.RetroScenarioContext;
import direct.reflect.facilitator.bdd.support.drivers.ColumnBoardDriver;
import direct.reflect.facilitator.bdd.support.drivers.RetroAccessDriver;
import direct.reflect.facilitator.bdd.support.drivers.RetroLifecycleDriver;
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

    private final RetroLifecycleDriver retroLifecycleDriver;
    private final RetroAccessDriver retroAccessDriver;
    private final ColumnBoardDriver columnBoardDriver;
    private final RetroScenarioContext context;

    @Given("I am a team member")
    public void iAmATeamMember() {
    }

    @Given("I have received a unique session link for a retrospective")
    public void iHaveReceivedAUniqueSessionLinkForARetrospective() {
        retroLifecycleDriver.createRetroAndGetLobbyUrl();
    }

    @When("I click on the session link")
    public void iClickOnTheSessionLink() {
        if (context.getSessionId() != null && !context.getSessionId().isBlank()) {
            retroAccessDriver.joinRetroAsGuest(context.getSessionId(), "Test Guest");
        } else {
            retroAccessDriver.authenticateAsGuest("Test Guest");
            retroAccessDriver.navigateToRetro(UUID.randomUUID().toString());
        }
    }

    @Then("I should be automatically logged into the session")
    public void iShouldBeAutomaticallyLoggedIntoTheSession() {
        retroAccessDriver.assertGuestAuthenticated();
        retroAccessDriver.assertJoinedSession(context.getSessionId());
    }

    @Then("I should not be prompted for username, password, or email")
    public void iShouldNotBePromptedForUsernamePasswordOrEmail() {
        retroAccessDriver.assertNoLoginPrompts();
    }

    @Then("I should see the retrospective interface immediately")
    public void iShouldSeeTheRetrospectiveInterfaceImmediately() {
        retroAccessDriver.assertRetroPageVisible();
    }

    @Given("I am participating in a retrospective session with pseudonym {string}")
    public void iAmParticipatingInARetrospectiveSessionWithPseudonym(String displayName) {
        retroLifecycleDriver.createRetroAndGetLobbyUrl();
        retroLifecycleDriver.advanceToPhase(2);
        columnBoardDriver.advanceFacilitatorUntilColumnBoardVisible();
        context.setCurrentPhaseNumber(2);
        context.setFacilitatorCookies(retroAccessDriver.captureCookies());
        retroAccessDriver.clearCookies();
        retroAccessDriver.joinRetroAsGuest(context.getSessionId(), displayName);
        columnBoardDriver.waitForColumnBoardVisible();
        context.setParticipantCookies(retroAccessDriver.captureCookies());
    }

    @When("I add a card, vote, or comment")
    public void iAddACardVoteOrComment() {
        String columnId = columnBoardDriver.findFirstColumnId();
        String noteContent = "BDD test note from " + System.currentTimeMillis();
        context.setLastNoteContent(noteContent);
        columnBoardDriver.addNoteAndWait(columnId, noteContent);
        retroAccessDriver.restoreCookies(context.getFacilitatorCookies());
        retroLifecycleDriver.reloadAndWait();
        columnBoardDriver.waitForColumnBoardVisible();
        retroLifecycleDriver.advanceOneStep();
    }

    @Then("all my contributions should display as coming from {string}")
    public void allMyContributionsShouldDisplayAsComingFrom(String displayName) {
        advanceToRevealStepAsFacilitator();
        rejoinAsParticipantAndAssertAuthor(displayName);
    }

    @Then("other participants should see my contributions attributed to {string}")
    public void otherParticipantsShouldSeeMyContributionsAttributedTo(String displayName) {
        retroAccessDriver.restoreCookies(context.getFacilitatorCookies());
        retroLifecycleDriver.reloadAndWait();
        columnBoardDriver.assertNoteShowsAuthor(displayName);
    }

    @Then("I should be able to identify my own contributions")
    public void iShouldBeAbleToIdentifyMyOwnContributions() {
        List<Cookie> participantCookies = context.getParticipantCookies();
        if (participantCookies == null) {
            throw new AssertionError("Participant cookies not captured — cannot verify own contributions.");
        }
        retroAccessDriver.restoreCookies(participantCookies);
        retroLifecycleDriver.reloadAndWait();
        columnBoardDriver.waitForColumnBoardVisible();
        columnBoardDriver.assertOwnNoteVisible(context.getLastNoteContent());
    }

    @Given("I was disconnected from a retrospective session")
    public void iWasDisconnectedFromARetrospectiveSession() {
        retroLifecycleDriver.createRetroAndGetLobbyUrl();
        retroLifecycleDriver.advanceToPhase(2);
        columnBoardDriver.advanceFacilitatorUntilColumnBoardVisible();
        context.setCurrentPhaseNumber(2);
        retroAccessDriver.clearCookies();
        retroAccessDriver.joinRetroAsGuest(context.getSessionId(), "Reconnect User");
        columnBoardDriver.waitForColumnBoardVisible();
        context.setParticipantCookies(retroAccessDriver.captureCookies());
        String columnId = columnBoardDriver.findFirstColumnId();
        String noteContent = "Note before disconnect " + System.currentTimeMillis();
        context.setLastNoteContent(noteContent);
        columnBoardDriver.addNoteAndWait(columnId, noteContent);
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
        retroAccessDriver.clearCookies();
        retroAccessDriver.restoreCookies(participantCookies);
        retroAccessDriver.navigateToRetro(context.getSessionId());
        retroAccessDriver.assertRetroPageVisible();
    }

    @Then("I should see the current phase and timer state")
    public void iShouldSeeTheCurrentPhaseAndTimerState() {
        int currentPhase = retroLifecycleDriver.detectCurrentPhaseNumber();
        Assertions.assertEquals(context.getCurrentPhaseNumber(), currentPhase,
            "Expected phase to be preserved after re-entry.");
    }

    @Then("I should see all contributions made during my absence")
    public void iShouldSeeAllContributionsMadeDuringMyAbsence() {
        columnBoardDriver.waitForColumnBoardVisible();
        columnBoardDriver.assertNoteVisible(context.getLastNoteContent());
    }

    @Then("I should be able to continue participating seamlessly")
    public void iShouldBeAbleToContinueParticipatingSeamlessly() {
        String columnId = columnBoardDriver.findFirstColumnId();
        String newNote = "Note after reconnect " + System.currentTimeMillis();
        columnBoardDriver.addNoteAndWait(columnId, newNote);
    }

    @Given("I have a session link for a retrospective")
    public void iHaveASessionLinkForARetrospective() {
    }

    @Given("the session has ended or the link is invalid")
    public void theSessionHasEndedOrTheLinkIsInvalid() {
    }

    @Then("I should see an error message indicating the session is unavailable")
    public void iShouldSeeAnErrorMessageIndicatingTheSessionIsUnavailable() {
        retroAccessDriver.assertErrorPage();
    }

    @Then("I should not be able to access the retrospective")
    public void iShouldNotBeAbleToAccessTheRetrospective() {
        retroAccessDriver.assertRetroContentNotVisible();
    }

    @Given("I am a team member with a session link")
    public void iAmATeamMemberWithASessionLink() {
        retroLifecycleDriver.createRetroAndGetLobbyUrl();
    }

    @When("I access the retrospective")
    public void iAccessTheRetrospective() {
        retroAccessDriver.clearCookies();
        retroAccessDriver.joinRetroAsGuest(context.getSessionId(), "Frictionless Guest");
    }

    @Then("I should not be required to create an account")
    public void iShouldNotBeRequiredToCreateAnAccount() {
        retroAccessDriver.assertPageLacksText("create an account");
        retroAccessDriver.assertPageLacksText("sign up");
        retroAccessDriver.assertPageLacksText("register");
    }

    @Then("I should not be required to remember a password")
    public void iShouldNotBeRequiredToRememberAPassword() {
        retroAccessDriver.assertNoLoginPrompts();
    }

    @Then("I should not be required to verify my email")
    public void iShouldNotBeRequiredToVerifyMyEmail() {
        retroAccessDriver.assertPageLacksText("verify your email");
        retroAccessDriver.assertPageLacksText("email verification");
    }

    @Then("I should not be required to complete a CAPTCHA")
    public void iShouldNotBeRequiredToCompleteACAPTCHA() {
        retroAccessDriver.assertPageLacksText("CAPTCHA");
        retroAccessDriver.assertPageLacksText("captcha");
        retroAccessDriver.assertPageLacksText("I'm not a robot");
    }

    @Then("I should not be required to provide any personal information")
    public void iShouldNotBeRequiredToProvideAnyPersonalInformation() {
        retroAccessDriver.assertNoPersonalInfoFields();
    }

    @Then("I should reach the retrospective interface in one click")
    public void iShouldReachTheRetrospectiveInterfaceInOneClick() {
        retroAccessDriver.assertRetroPageVisible();
    }

    private void advanceToRevealStepAsFacilitator() {
        List<Cookie> facilitatorCookies = context.getFacilitatorCookies();
        if (facilitatorCookies == null) {
            throw new AssertionError("Facilitator cookies not captured.");
        }
        retroAccessDriver.restoreCookies(facilitatorCookies);
        retroLifecycleDriver.reloadAndWait();
        columnBoardDriver.waitForColumnBoardVisible();
        retroLifecycleDriver.advanceOneStep();
    }

    private void rejoinAsParticipantAndAssertAuthor(String displayName) {
        List<Cookie> participantCookies = context.getParticipantCookies();
        if (participantCookies == null) {
            throw new AssertionError("Participant cookies not captured.");
        }
        retroAccessDriver.restoreCookies(participantCookies);
        retroLifecycleDriver.reloadAndWait();
        columnBoardDriver.waitForColumnBoardVisible();
        columnBoardDriver.assertNoteShowsAuthor(displayName);
    }
}
