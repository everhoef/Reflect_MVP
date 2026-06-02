package direct.reflect.facilitator.bdd.support.drivers;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import direct.reflect.facilitator.bdd.support.PlaywrightWorld;
import io.cucumber.spring.ScenarioScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import static direct.reflect.facilitator.bdd.support.selectors.RetroSelectors.COLUMN_BOARD_ITEM;
import static direct.reflect.facilitator.bdd.support.selectors.RetroSelectors.NEXT_STEP_BUTTON;
import static direct.reflect.facilitator.bdd.support.selectors.RetroSelectors.OWN_NOTE_EDITABLE;
import static direct.reflect.facilitator.bdd.support.selectors.RetroSelectors.RETRO_CONTENT;
import static direct.reflect.facilitator.bdd.support.selectors.RetroSelectors.authorTextSelector;
import static direct.reflect.facilitator.bdd.support.selectors.RetroSelectors.noteInputSelector;
import static direct.reflect.facilitator.bdd.support.selectors.RetroSelectors.noteSubmitSelector;
import static direct.reflect.facilitator.bdd.support.selectors.RetroSelectors.noteTextSelector;

@ScenarioScope
@Component
@RequiredArgsConstructor
@Slf4j
public class ColumnBoardDriver {
    private static final int DEFAULT_TIMEOUT_MS = 5_000;
    private static final int LONG_TIMEOUT_MS = 15_000;
    private static final int MAX_RELOAD_RETRIES = 2;

    private final PlaywrightWorld world;
    private final RetroLifecycleDriver retroLifecycleDriver;

    public String findFirstColumnId() {
        Locator columns = world.getPage().locator(COLUMN_BOARD_ITEM);
        if (columns.count() == 0) {
            throw new AssertionError("No columns found on the page.");
        }
        String testId = columns.first().getAttribute("data-testid");
        return testId.replace("column-", "");
    }

    public void advanceFacilitatorUntilColumnBoardVisible() {
        Page page = world.getPage();
        for (int attempt = 0; attempt < 12; attempt++) {
            if (page.locator(COLUMN_BOARD_ITEM).count() > 0) {
                return;
            }
            if (page.locator(NEXT_STEP_BUTTON).count() == 0) {
                break;
            }
            retroLifecycleDriver.advanceOneStep();
        }
        throw new AssertionError("Expected a multi-column board step with semantic column selectors, but none was found.");
    }

    public void waitForColumnBoardVisible() {
        world.getPage().waitForSelector(COLUMN_BOARD_ITEM, new Page.WaitForSelectorOptions().setTimeout(DEFAULT_TIMEOUT_MS));
    }

    public void addNoteAndWait(String columnId, String noteContent) {
        Page page = world.getPage();
        page.waitForSelector(noteInputSelector(columnId), new Page.WaitForSelectorOptions().setTimeout(LONG_TIMEOUT_MS));
        page.fill(noteInputSelector(columnId), noteContent);
        page.click(noteSubmitSelector(columnId));
        page.waitForSelector(noteTextSelector(noteContent), new Page.WaitForSelectorOptions().setTimeout(LONG_TIMEOUT_MS));
    }

    public void assertNoteVisible(String noteContent) {
        try {
            assertVisibleWithReloadRecovery(noteTextSelector(noteContent), noteContent, false);
        } catch (RuntimeException e) {
            throw new AssertionError("Expected note to be visible: " + noteContent, e);
        }
    }

    public void assertNoteShowsAuthor(String displayName) {
        Locator authorBadge = world.getPage().locator(RETRO_CONTENT).locator(authorTextSelector(displayName));
        if (authorBadge.count() == 0) {
            String bodyText = world.getPage().locator(RETRO_CONTENT).textContent();
            throw new AssertionError("Expected note to show author '" + displayName + "'. Retro content text: " + bodyText);
        }
    }

    public void assertOwnNoteVisible(String noteContent) {
        try {
            assertVisibleWithReloadRecovery(OWN_NOTE_EDITABLE, noteContent, true);
        } catch (RuntimeException e) {
            throw new AssertionError("Expected to find own contribution with edit affordance: " + noteContent, e);
        }
    }

    private void assertVisibleWithReloadRecovery(String selector, String noteContent, boolean requireOwnEditable) {
        RuntimeException lastFailure = null;

        for (int attempt = 0; attempt <= MAX_RELOAD_RETRIES; attempt++) {
            try {
                world.getPage().waitForSelector(selector,
                    new Page.WaitForSelectorOptions().setTimeout(attempt == 0 ? DEFAULT_TIMEOUT_MS : LONG_TIMEOUT_MS));

                if (requireOwnEditable) {
                    Locator ownNote = world.getPage().locator(selector)
                        .filter(new Locator.FilterOptions().setHasText(noteContent));
                    if (ownNote.count() == 0) {
                        throw new AssertionError("Expected to find own contribution with edit affordance.");
                    }
                }

                return;
            } catch (RuntimeException e) {
                lastFailure = e;
                if (attempt == MAX_RELOAD_RETRIES) {
                    break;
                }

                log.warn("Selector '{}' for note '{}' was not visible on attempt {}/{}. Reloading retro page before retry.",
                    selector, noteContent, attempt + 1, MAX_RELOAD_RETRIES + 1);
                retroLifecycleDriver.reloadAndWait();
                waitForColumnBoardVisible();
            }
        }

        if (lastFailure == null) {
            throw new AssertionError("Expected selector to become visible but no failure details were captured.");
        }
        throw lastFailure;
    }
}
