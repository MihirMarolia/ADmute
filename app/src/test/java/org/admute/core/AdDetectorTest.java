package org.admute.core;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

public class AdDetectorTest {
    private final AdDetector detector = new AdDetector();
    private AdDetector.Evidence detect(String pkg, String value, boolean control) {
        return detector.detect(pkg, Arrays.asList(new AdDetector.Label(value, control)), true);
    }
    @Test public void recognizesSpecificPlayerLabelsInAllApps() {
        for (String pkg : AdDetector.PACKAGES) {
            for (String value : Arrays.asList("Ad 1 of 3", "AD 2 / 3", "Advertisements remaining: 30 seconds", "Ad left: 0:30", "Ad · 0:15"))
                assertEquals(pkg + ": " + value, AdDetector.Evidence.AD, detect(pkg, value, true));
        }
    }
    @Test public void adaptersDoNotLeakToOtherApps() {
        assertEquals(AdDetector.Evidence.AD, detect(AdDetector.YOUTUBE, "Skip ad", true));
        assertEquals(AdDetector.Evidence.AD, detect(AdDetector.YOUTUBE, "Skip ads in 5 seconds", true));
        assertEquals(AdDetector.Evidence.CLEAR, detect(AdDetector.PLEX, "Skip ad", true));
        assertEquals(AdDetector.Evidence.AD, detect(AdDetector.TUBI, "Commercial break: 0:30", true));
        assertEquals(AdDetector.Evidence.CLEAR, detect("com.netflix.ninja", "Ad 1 of 3", true));
    }
    @Test public void rejectsMovieLaunchAndBrowsingFalsePositives() {
        for (String pkg : AdDetector.PACKAGES) {
            for (String value : Arrays.asList("Ad Astra", "Skip intro", "Skip credits", "Loading", "Sponsored", "Ad", "Advertisement", "Watch ads for free movies", "A story about ad 1 of 3", "Skip ad: the movie", "Play", "Trailer"))
                assertEquals(value, AdDetector.Evidence.CLEAR, detect(pkg, value, true));
            assertEquals(AdDetector.Evidence.CLEAR, detect(pkg, "Ad 1 of 3", false));
        }
    }
    @Test public void missingOrTruncatedTreesAreUnknownButPositiveEvidenceWins() {
        assertEquals(AdDetector.Evidence.UNKNOWN, detector.detect(AdDetector.YOUTUBE, Collections.emptyList(), false));
        assertEquals(AdDetector.Evidence.UNKNOWN, detector.detect(AdDetector.YOUTUBE, Arrays.asList(new AdDetector.Label(null, true)), false));
        assertEquals(AdDetector.Evidence.AD, detector.detect(AdDetector.YOUTUBE, Arrays.asList(new AdDetector.Label("Ad 1 of 2", true)), false));
    }
    @Test public void normalizesWhitespaceAndCase() {
        assertEquals(AdDetector.Evidence.AD, detect(AdDetector.YOUTUBE, "  SKIP\n AD  ", true));
    }
    @Test public void youtubePairWorksWithGenericAccessibleViews() {
        assertEquals(AdDetector.Evidence.AD, detector.detect(AdDetector.YOUTUBE, Arrays.asList(
                new AdDetector.Label("Sponsored", false), new AdDetector.Label("Send to phone", false)), true));
    }
    @Test public void youtubePairDoesNotActivateOnEitherLabelAloneOrAnotherApp() {
        assertEquals(AdDetector.Evidence.CLEAR, detect(AdDetector.YOUTUBE, "Sponsored", true));
        assertEquals(AdDetector.Evidence.CLEAR, detect(AdDetector.YOUTUBE, "Send to phone", true));
        for (String pkg : Arrays.asList(AdDetector.TUBI, AdDetector.PLEX))
            assertEquals(AdDetector.Evidence.CLEAR, detector.detect(pkg, Arrays.asList(
                    new AdDetector.Label("Sponsored", true), new AdDetector.Label("Send to phone", true)), true));
    }
    @Test public void youtubePairDoesNotUseExcludedTitlesOrSubstrings() {
        assertEquals(AdDetector.Evidence.CLEAR, detector.detect(AdDetector.YOUTUBE, Arrays.asList(
                new AdDetector.Label("Sponsored", false, false), new AdDetector.Label("Send to phone", true)), true));
        assertEquals(AdDetector.Evidence.CLEAR, detector.detect(AdDetector.YOUTUBE, Arrays.asList(
                new AdDetector.Label("Sponsored video about a phone", true), new AdDetector.Label("Send to phone", true)), true));
    }
    @Test public void emptyVirtualLabelsAreNotMeaningful() {
        for (String value : Arrays.asList(null, "", "   ", "\n\t", "\u00a0", "\u200b"))
            assertFalse(AdDetector.meaningful(value));
        assertTrue(AdDetector.meaningful("Sponsored"));
        assertEquals("SPONSORED", AdDetector.marker(" Sponsored "));
        assertEquals("", AdDetector.marker("My sponsored movie"));
    }
}
