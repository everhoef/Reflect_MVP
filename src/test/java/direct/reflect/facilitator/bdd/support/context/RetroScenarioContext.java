package direct.reflect.facilitator.bdd.support.context;

import com.microsoft.playwright.options.Cookie;
import io.cucumber.spring.ScenarioScope;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.util.List;

@ScenarioScope
@Component
@Getter
public class RetroScenarioContext {
    private boolean retroReady;
    private String sessionId;
    private int currentPhaseNumber;
    private boolean lastAdvanceTriggered;
    private String lastNoteContent;
    private List<Cookie> facilitatorCookies;
    private List<Cookie> participantCookies;

    public void setRetroReady(boolean retroReady) {
        this.retroReady = retroReady;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    /**
     * System state — should only be called by drivers that detect phase from browser state.
     * Step definitions must NOT call this directly.
     */
    public void setCurrentPhaseNumber(int currentPhaseNumber) {
        this.currentPhaseNumber = currentPhaseNumber;
    }

    public void setLastAdvanceTriggered(boolean lastAdvanceTriggered) {
        this.lastAdvanceTriggered = lastAdvanceTriggered;
    }

    public void setLastNoteContent(String lastNoteContent) {
        this.lastNoteContent = lastNoteContent;
    }

    public void setFacilitatorCookies(List<Cookie> facilitatorCookies) {
        this.facilitatorCookies = facilitatorCookies;
    }

    public void setParticipantCookies(List<Cookie> participantCookies) {
        this.participantCookies = participantCookies;
    }
}
