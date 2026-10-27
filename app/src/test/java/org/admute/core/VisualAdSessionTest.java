package org.admute.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class VisualAdSessionTest {
    private final VisualAdSession s = new VisualAdSession();
    private void protect() { assertFalse(s.observe(true, true, 0)); assertTrue(s.observe(true, true, 700)); }
    @Test public void repeatedCachedFrameCannotConfirm() {
        assertFalse(s.observe(true, true, 0)); assertFalse(s.observe(true, true, 0));
        assertFalse(s.observe(true, true, 100)); assertTrue(s.observe(true, true, 700));
    }
    @Test public void staleCandidateCannotConfirm() {
        assertFalse(s.observe(true, true, 0)); assertFalse(s.observe(true, true, 3000));
        assertTrue(s.observe(true, true, 3700));
    }
    @Test public void markerWindowKeepsProtectionAcrossPods() {
        protect(); assertTrue(s.observe(false, true, 1400));
        assertTrue(s.observe(false, false, 2100)); assertTrue(s.observe(false, true, 2800));
    }
    @Test public void clearRequiresThreeFreshFramesAnd1500ms() {
        protect(); assertTrue(s.observe(false, false, 1400)); assertTrue(s.observe(false, false, 2100));
        assertTrue(s.observe(false, false, 2800)); assertFalse(s.observe(false, false, 3500));
    }
    @Test public void failedCaptureHoldsCoverAndBreaksClearSequence() {
        protect(); assertTrue(s.observe(false, false, 1400)); s.failed(); assertTrue(s.active());
        assertTrue(s.observe(false, false, 2100)); assertTrue(s.observe(false, false, 2800));
        assertTrue(s.observe(false, false, 3500)); assertFalse(s.observe(false, false, 4200));
    }
    @Test public void captureGapCannotBeCountedAsClearTime() {
        protect(); assertTrue(s.observe(false, false, 1400)); assertTrue(s.observe(false, false, 9000));
    }
    @Test public void navigationOrDisableResetsOwnership() {
        protect(); s.reset(); assertFalse(s.active()); assertFalse(s.observe(true, true, 1400));
    }
}
