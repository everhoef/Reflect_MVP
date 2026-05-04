package direct.reflect.facilitator.bdd.support.context;

import io.cucumber.spring.ScenarioScope;
import lombok.Data;
import org.springframework.stereotype.Component;

@ScenarioScope
@Component
@Data
public class RetroScenarioContext {
    private boolean retroReady;
    private String sessionId;
    private int currentPhaseNumber;
    private boolean lastAdvanceTriggered;
}
