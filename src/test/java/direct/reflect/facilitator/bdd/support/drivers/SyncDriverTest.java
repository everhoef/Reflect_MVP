package direct.reflect.facilitator.bdd.support.drivers;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import direct.reflect.facilitator.bdd.support.PlaywrightWorld;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.atomic.AtomicBoolean;

import static direct.reflect.facilitator.bdd.support.selectors.RetroSelectors.RETRO_CONTENT;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SyncDriverTest {

    @Mock
    private PlaywrightWorld world;

    @Mock
    private Page page;

    @Mock
    private Locator retroContent;

    @InjectMocks
    private SyncDriver syncDriver;

    @Test
    void waitForPhaseOrStepChange_returnsWithoutReloadWhenSettledChangeAppearsImmediately() {
        SyncDriver.ShellSnapshot previousSnapshot = snapshot("SET_THE_STAGE", "1", "reconciling", "true");
        stubSettledSnapshot(snapshot("GATHER_DATA", "2", "settled", "true"));

        syncDriver.waitForPhaseOrStepChange(previousSnapshot);

        verify(page, never()).reload(any(Page.ReloadOptions.class));
    }

    @Test
    void waitForPhaseOrStepChange_recoversFromStaleShellAfterOneReload() {
        SyncDriver.ShellSnapshot previousSnapshot = snapshot("SET_THE_STAGE", "1", "settled", "true");
        AtomicBoolean reloaded = new AtomicBoolean(false);
        stubReloadAwareSnapshots(
            snapshot("SET_THE_STAGE", "1", "reconciling", "false"),
            snapshot("GATHER_DATA", "2", "settled", "true"),
            reloaded
        );
        when(page.reload(any(Page.ReloadOptions.class))).thenAnswer(invocation -> {
            reloaded.set(true);
            return null;
        });
        when(page.waitForSelector(eq(RETRO_CONTENT), any(Page.WaitForSelectorOptions.class))).thenReturn(null);

        syncDriver.waitForPhaseOrStepChange(previousSnapshot);

        verify(page, times(1)).reload(any(Page.ReloadOptions.class));
        verify(page).waitForSelector(eq(RETRO_CONTENT), any(Page.WaitForSelectorOptions.class));
    }

    @Test
    void waitForPhaseOrStepChange_throwsWhenReloadFallbackStillShowsNoProgress() {
        SyncDriver.ShellSnapshot previousSnapshot = snapshot("SET_THE_STAGE", "1", "settled", "true");
        stubSettledFailureSnapshot(snapshot("SET_THE_STAGE", "1", "reconciling", "false"));
        when(page.reload(any(Page.ReloadOptions.class))).thenReturn(null);
        when(page.waitForSelector(eq(RETRO_CONTENT), any(Page.WaitForSelectorOptions.class))).thenReturn(null);

        AssertionError error = assertThrows(AssertionError.class, () -> syncDriver.waitForPhaseOrStepChange(previousSnapshot));

        assertNotNull(error.getMessage());
        assertAll(
            () -> assertTrue(error.getMessage().contains("phase")),
            () -> assertTrue(error.getMessage().contains("step")),
            () -> assertTrue(error.getMessage().contains("sync-state")),
            () -> assertTrue(error.getMessage().contains("connection")),
            () -> assertTrue(error.getMessage().contains("refresh"))
        );
    }

    private void stubSettledSnapshot(SyncDriver.ShellSnapshot settledSnapshot) {
        when(world.getPage()).thenReturn(page);
        when(page.locator(RETRO_CONTENT)).thenReturn(retroContent);
        when(retroContent.getAttribute("data-phase")).thenReturn(settledSnapshot.phase());
        when(retroContent.getAttribute("data-step-index")).thenReturn(settledSnapshot.stepIndex());
        when(retroContent.getAttribute("data-sync-state")).thenReturn(settledSnapshot.syncState());
        when(retroContent.getAttribute("data-sse-connected")).thenReturn(settledSnapshot.sseConnected());
    }

    private void stubReloadAwareSnapshots(SyncDriver.ShellSnapshot staleSnapshot, SyncDriver.ShellSnapshot settledSnapshot, AtomicBoolean reloaded) {
        when(world.getPage()).thenReturn(page);
        when(page.locator(RETRO_CONTENT)).thenReturn(retroContent);
        when(retroContent.getAttribute("data-phase")).thenAnswer(invocation -> reloaded.get() ? settledSnapshot.phase() : staleSnapshot.phase());
        when(retroContent.getAttribute("data-step-index")).thenAnswer(invocation -> reloaded.get() ? settledSnapshot.stepIndex() : staleSnapshot.stepIndex());
        when(retroContent.getAttribute("data-sync-state")).thenAnswer(invocation -> reloaded.get() ? settledSnapshot.syncState() : staleSnapshot.syncState());
        when(retroContent.getAttribute("data-sse-connected")).thenAnswer(invocation -> reloaded.get() ? settledSnapshot.sseConnected() : staleSnapshot.sseConnected());
    }

    private void stubSettledFailureSnapshot(SyncDriver.ShellSnapshot staleSnapshot) {
        when(world.getPage()).thenReturn(page);
        when(page.locator(RETRO_CONTENT)).thenReturn(retroContent);
        when(retroContent.getAttribute("data-phase")).thenReturn(staleSnapshot.phase());
        when(retroContent.getAttribute("data-step-index")).thenReturn(staleSnapshot.stepIndex());
        when(retroContent.getAttribute("data-sync-state")).thenReturn(staleSnapshot.syncState());
        when(retroContent.getAttribute("data-sse-connected")).thenReturn(staleSnapshot.sseConnected());
    }

    private static SyncDriver.ShellSnapshot snapshot(String phase, String stepIndex, String syncState, String sseConnected) {
        return new SyncDriver.ShellSnapshot(phase, stepIndex, syncState, sseConnected);
    }
}
