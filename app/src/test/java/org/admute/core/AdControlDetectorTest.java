package org.admute.core;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class AdControlDetectorTest {
    private final AdControlDetector detector = new AdControlDetector();
    private AdControlDetector.Candidate item(String text, int left, int top, int right, int bottom, boolean clickable) {
        return new AdControlDetector.Candidate(text, left, top, right, bottom, clickable);
    }

    @Test public void sponsoredDismissButtonIsSelectedOnCurrentScreenLayout() {
        AdControlDetector.Target target = detector.findTarget(AdDetector.YOUTUBE, Arrays.asList(
                item("Sponsored · Scotiabank", 1420, 190, 1690, 220, false),
                item("Dismiss", 1430, 900, 1590, 975, true)), 1920, 1080);
        assertNotNull(target);
        assertEquals(AdControlDetector.Action.DISMISS, target.action());
        assertEquals("Dismiss", target.candidate().text());
    }

    @Test public void dismissRequiresSponsoredContextAndClickableTarget() {
        assertNull(detector.findTarget(AdDetector.YOUTUBE, Arrays.asList(
                item("Dismiss", 1430, 900, 1590, 975, true)), 1920, 1080));
        assertNull(detector.findTarget(AdDetector.YOUTUBE, Arrays.asList(
                item("Sponsored", 1420, 190, 1690, 220, false),
                item("Dismiss", 1430, 900, 1590, 975, false)), 1920, 1080));
    }

    @Test public void skipRequiresExactYouTubeControlInLowerRight() {
        AdControlDetector.Target target = detector.findTarget(AdDetector.YOUTUBE, Arrays.asList(
                item("Skip ad", 1500, 900, 1650, 980, true)), 1920, 1080);
        assertNotNull(target);
        assertEquals(AdControlDetector.Action.SKIP, target.action());
        assertNull(detector.findTarget(AdDetector.YOUTUBE, Arrays.asList(
                item("Skip ad in 5 seconds", 1500, 900, 1650, 980, true)), 1920, 1080));
        assertNull(detector.findTarget(AdDetector.PLEX, Arrays.asList(
                item("Skip ad", 1500, 900, 1650, 980, true)), 1920, 1080));
    }

    @Test public void youtubeSkipButtonWithSponsoredContextCanOmitTheWordAd() {
        AdControlDetector.Target target = detector.findTarget(AdDetector.YOUTUBE, Arrays.asList(
                item("Sponsored · Scotiabank", 100, 900, 450, 980, false),
                item("Skip", 1650, 930, 1740, 995, true)), 1920, 1080);
        assertNotNull(target);
        assertEquals(AdControlDetector.Action.SKIP, target.action());
        assertNull(detector.findTarget(AdDetector.YOUTUBE, Arrays.asList(
                item("Skip", 1650, 930, 1740, 995, true)), 1920, 1080));
    }

    @Test public void ignoresControlsOutsideLowerRightOrUnsupportedApps() {
        assertNull(detector.findTarget(AdDetector.YOUTUBE, Arrays.asList(
                item("Skip ad", 200, 200, 350, 260, true)), 1920, 1080));
        assertNull(detector.findTarget("com.netflix.ninja", Arrays.asList(
                item("Skip ad", 1500, 900, 1650, 980, true)), 1920, 1080));
    }
    @Test public void dismissIsNotEnabledForUninspectedStreamingLayouts() {
        for (String pkg : Arrays.asList(AdDetector.TUBI, AdDetector.PLEX))
            assertNull(detector.findTarget(pkg, Arrays.asList(
                    item("Sponsored", 100, 900, 450, 980, false),
                    item("Dismiss", 1430, 900, 1590, 975, true)), 1920, 1080));
    }
    @Test public void offscreenSponsoredTextDoesNotAuthorizeGenericSkip() {
        assertNull(detector.findTarget(AdDetector.YOUTUBE, Arrays.asList(
                item("Sponsored", -100, 900, 450, 980, false),
                item("Skip", 1650, 930, 1740, 995, true)), 1920, 1080));
    }
    @Test public void malformedAndCountdownCandidatesAreIgnored() {
        assertNull(detector.findTarget(AdDetector.YOUTUBE, null, 1920, 1080));
        assertNull(detector.findTarget(AdDetector.YOUTUBE, Arrays.asList(
                null, item(null, 1650, 930, 1740, 995, true),
                item("Skip ad", 1650, 930, 2500, 995, true),
                item("Sponsored", 100, 900, 450, 980, false),
                item("Skip in 1", 1650, 930, 1740, 995, true)), 1920, 1080));
    }
}
