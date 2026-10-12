package org.admute.core;

import org.junit.Test;
import static org.junit.Assert.*;
import static org.admute.core.AdDetector.Evidence.*;

public class AdSessionTest {
    private final AdSession session = new AdSession();
    private boolean update(AdDetector.Evidence evidence, long now) {
        return session.update(AdDetector.YOUTUBE, evidence, now, true);
    }
    private void protect() { assertFalse(update(AD, 0)); assertTrue(update(AD, 350)); }
    @Test public void requiresRepeatedEvidence() {
        assertFalse(update(AD, 0)); assertFalse(update(AD, 200)); assertTrue(update(AD, 350));
    }
    @Test public void transientMovieLaunchLabelDoesNotTrigger() {
        assertFalse(update(AD, 0)); assertFalse(update(CLEAR, 350)); assertFalse(update(AD, 700));
    }
    @Test public void unknownBetweenCandidatesCancelsConfirmation() {
        assertFalse(update(AD, 0)); assertFalse(update(UNKNOWN, 350)); assertFalse(update(AD, 700));
    }
    @Test public void smallGapsBetweenAdsStayProtected() {
        protect(); assertTrue(update(CLEAR, 1000)); assertTrue(update(AD, 1700));
        assertTrue(update(CLEAR, 2000)); assertTrue(update(CLEAR, 3400)); assertFalse(update(CLEAR, 3500));
    }
    @Test public void unknownReadHasBoundedGraceAndRequiresClearBeforeReentry() {
        protect(); assertTrue(update(UNKNOWN, 8000)); assertFalse(update(UNKNOWN, 8350));
        assertFalse(update(AD, 9000)); assertFalse(update(CLEAR, 9500)); assertFalse(update(CLEAR, 11000));
        assertFalse(update(AD, 12000)); assertTrue(update(AD, 12350));
    }
    @Test public void hardTimeoutIsNotExtendedByPositiveLabels() {
        protect(); assertTrue(update(AD, 170000)); assertFalse(update(AD, 180350));
        assertFalse(update(AD, 181000)); assertEquals(AdSession.State.COOLDOWN, session.state());
    }
    @Test public void changingAppRequiresNewConfirmation() {
        protect(); assertFalse(session.update(AdDetector.TUBI, AD, 700, true));
        assertTrue(session.update(AdDetector.TUBI, AD, 1050, true));
    }
    @Test public void unsupportedAppAndDisableReleaseImmediately() {
        protect(); assertFalse(session.update("launcher", AD, 700, true));
        protect(); assertFalse(session.update(AdDetector.YOUTUBE, AD, 700, false));
    }
    @Test public void unknownDoesNotCountAsClear() {
        protect(); assertTrue(update(CLEAR, 1000)); assertTrue(update(UNKNOWN, 2000));
        assertTrue(update(CLEAR, 2500)); assertTrue(update(CLEAR, 3500)); assertFalse(update(CLEAR, 4000));
    }
}
