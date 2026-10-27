package org.admute.core;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Random;

public class VisualAdDetectorTest {
    private final VisualAdDetector detector = new VisualAdDetector();
    private int[] fixture(String name) throws Exception {
        int[] pixels = new int[1920 * 1080];
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                getClass().getResourceAsStream("/visual/" + name + ".pixels"), java.nio.charset.StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] dimensions = line.trim().split(" ");
                int x = Integer.parseInt(dimensions[0]), y = Integer.parseInt(dimensions[1]);
                int width = Integer.parseInt(dimensions[2]), height = Integer.parseInt(dimensions[3]);
                for (int row = 0; row < height; row++) {
                    String values = reader.readLine();
                    for (int col = 0; col < width; col++)
                        if (values.charAt(col) == '#') pixels[(y + row) * 1920 + x + col] = 0xffffffff;
                }
            }
        }
        return pixels;
    }
    @Test public void independentlyCapturedAdLayoutsMatch() throws Exception {
        for (String name : new String[] {"intact", "coca-cola", "lexus"}) {
            VisualAdDetector.Result r = detector.detect(fixture(name), 1920, 1080);
            assertTrue(name, r.ad());
            assertTrue(r.sponsored.left >= 170 && r.sponsored.right <= 300);
            assertTrue(r.phone.left > 1300);
        }
    }
    @Test public void sponsoredAloneCannotTrigger() throws Exception {
        int[] pixels = fixture("intact");
        for (int y = 900; y < 1040; y++) Arrays.fill(pixels, y * 1920 + 900, y * 1920 + 1800, 0);
        VisualAdDetector.Result r = detector.detect(pixels, 1920, 1080);
        assertNotNull(r.sponsored); assertFalse(r.ad());
    }
    @Test public void phoneAloneCannotTrigger() throws Exception {
        int[] pixels = fixture("intact");
        for (int y = 900; y < 1040; y++) Arrays.fill(pixels, y * 1920, y * 1920 + 800, 0);
        assertFalse(detector.detect(pixels, 1920, 1080).ad());
    }
    @Test public void matchingWordsInTitlesCannotTrigger() throws Exception {
        int[] pixels = fixture("intact"), moved = new int[pixels.length];
        System.arraycopy(pixels, 900 * 1920, moved, 100 * 1920, 140 * 1920);
        assertFalse(detector.detect(moved, 1920, 1080).ad());
    }
    @Test public void solidFramesAndNoiseDoNotTrigger() {
        int[] pixels = new int[1920 * 1080];
        for (int color : new int[] {0, 0xff080b0d, 0xffffffff, 0xffaaaaaa}) {
            Arrays.fill(pixels, color); assertFalse(detector.detect(pixels, 1920, 1080).ad());
        }
        Random random = new Random(84);
        for (int i = 0; i < pixels.length; i++) pixels[i] = random.nextBoolean() ? 0xffffffff : 0;
        assertFalse(detector.detect(pixels, 1920, 1080).ad());
    }
    @Test public void otherResolutionsAreExplicitlyUnsupported() {
        assertFalse(detector.detect(new int[1280 * 720], 1280, 720).supported);
    }
    @Test public void oddPixelButtonShiftStillMatchesAndFooterAgrees() throws Exception {
        int[] pixels = fixture("intact");
        for (int y = 950; y < 990; y++) {
            int[] row = Arrays.copyOfRange(pixels, y * 1920 + 1440, y * 1920 + 1620);
            Arrays.fill(pixels, y * 1920 + 1440, y * 1920 + 1620, 0);
            System.arraycopy(row, 0, pixels, y * 1920 + 1557, row.length);
        }
        VisualAdDetector.Result full = detector.detect(pixels, 1920, 1080);
        VisualAdDetector.Result footer = detector.detectFooter(Arrays.copyOfRange(pixels, 900 * 1920, pixels.length), 1920, 180);
        assertTrue(full.ad()); assertTrue(footer.ad());
        assertEquals(1565, full.phone.left);
        assertEquals(full.sponsored.top, footer.sponsored.top);
        assertEquals(full.phone.left, footer.phone.left);
    }
}
