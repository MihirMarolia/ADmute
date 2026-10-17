package org.admute.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class AdControlGuardTest {
    @Test public void discardsScansAfterNavigationSettingsOrPause() {
        AdControlGuard guard = new AdControlGuard();
        long token = guard.generation();
        assertTrue(guard.current(token, 100, 200));
        guard.invalidate();
        assertFalse(guard.current(token, 100, 200));
    }

    @Test public void discardsExpiredOrInvalidTimeSamples() {
        AdControlGuard guard = new AdControlGuard();
        long token = guard.generation();
        assertTrue(guard.current(token, 100, 2600));
        assertFalse(guard.current(token, 100, 2601));
        assertFalse(guard.current(token, 100, 99));
    }

    @Test public void requiresTwoClearReadsBeforeRepeatingAnAction() {
        AdControlGuard guard = new AdControlGuard();
        guard.activated("youtube:SKIP");
        assertFalse(guard.canActivate("youtube:SKIP"));
        guard.invalidate(); // The window event from our own tap must not rearm it.
        assertFalse(guard.canActivate("youtube:SKIP"));
        guard.observe(false);
        assertFalse(guard.canActivate("youtube:SKIP"));
        guard.observe(true);
        guard.observe(false);
        assertFalse(guard.canActivate("youtube:SKIP"));
        guard.observe(false);
        assertTrue(guard.canActivate("youtube:SKIP"));
    }

    @Test public void newOwnerResetsActionsAndInvalidatesOldScans() {
        AdControlGuard guard = new AdControlGuard();
        long token = guard.generation();
        guard.activated("youtube:DISMISS");
        guard.reset();
        assertTrue(guard.canActivate("youtube:DISMISS"));
        assertFalse(guard.current(token, 100, 200));
    }
    @Test public void accessibleTreeCannotRearmOcrControl() {
        AdControlGuard guard = new AdControlGuard();
        guard.activated("youtube:SKIP", AdControlGuard.Source.OCR);
        guard.observe(false); guard.observe(false);
        assertFalse(guard.canActivate("youtube:SKIP"));
        guard.observe(false, AdControlGuard.Source.OCR);
        assertFalse(guard.canActivate("youtube:SKIP"));
        guard.observe(false, AdControlGuard.Source.OCR);
        assertTrue(guard.canActivate("youtube:SKIP"));
    }
}
