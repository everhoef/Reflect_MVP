package direct.reflect.facilitator.bdd.support.context;

import com.microsoft.playwright.options.Cookie;
import io.cucumber.spring.ScenarioScope;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.util.List;

@ScenarioScope
@Component
@Data
public class RetroScenarioContext {
    private boolean retroReady;
    private String sessionId;
    private boolean lastAdvanceTriggered;
    private String lastNoteContent;
    private List<Cookie> facilitatorCookies;
    private List<Cookie> participantCookies;
}
