package org.admute.core;

/** Local pixel matching for the inspected English YouTube TV 1080p player layout.
 * No native code, model download, arbitrary text recognition, or stored frames.
 * These two independent UI markers must both match before an ad is confirmed.
 */
public final class VisualAdDetector {
    public static final int WIDTH = 1920, HEIGHT = 1080;
    public static final class Box {
        public final int left, top, right, bottom;
        public Box(int left, int top, int right, int bottom) {
            this.left = left; this.top = top; this.right = right; this.bottom = bottom;
        }
    }
    public static final class Result {
        public final boolean supported;
        public final Box sponsored, phone;
        Result(boolean supported, Box sponsored, Box phone) {
            this.supported = supported; this.sponsored = sponsored; this.phone = phone;
        }
        public boolean ad() { return sponsored != null && phone != null; }
    }
    private static final String[] SPONSORED = {
        ".............................................................",
        "..###...................................................##...",
        ".######.................................................##...",
        ".##..##.####....###..##.##...###....###..##.#..###....####...",
        ".###....#####..#####.######.#####..#####.####.#####..#####...",
        ".#####..##.######.######.##.##.######.######..##.######.##...",
        "...####.##..####...####..##.#####.##...####...########..##...",
        "##...##.##..#####.#####..##...#######.#####...########..##...",
        ".######.######.#####.##..##.######.#####.##...#####.######...",
        ".#####..#####..#####.##..##.#####..#####.##....####..#####...",
        "........##...................................................",
        "........##...................................................",
        "........##..................................................."
    };
    private static final String[] PHONE = {
        "...............................................................................",
        "...............................................................................",
        "...............................................................................",
        "...###...................##.........................##.........................",
        "..#####..................##.....##..................##.........................",
        ".##...##..###..#####...####....####...##......####..#####....##...#####...###..",
        ".###.....#####.######.#####....####.######....#####.######.######.#####..#####.",
        "..####..##..##.##..##.##.##.....##..##..##....##..####..##.##..##.##..####..##.",
        "....##########.##..####..##.....##..##..##....##..####..##.##..##.##..########.",
        ".##...####.....##..####..##.....##..##..##....##..####..##.##..##.##..####.....",
        ".###.######.##.##..##.##.##......##.######....##.##.##..##.######.##..#####.##.",
        "..#####..#####.##..##.#####......##..####.....#####.##..##..####..##..##.#####.",
        "..............................................##...............................",
        "..............................................##...............................",
        "..............................................##..............................."
    };
    private static final Pattern SPONSOR_PATTERN = new Pattern(SPONSORED);
    private static final Pattern PHONE_PATTERN = new Pattern(PHONE);
    public Result detect(int[] pixels, int width, int height) {
        if (width != WIDTH || height != HEIGHT || pixels.length != width * height)
            return new Result(false, null, null);
        return detectPixels(pixels, 0);
    }
    public Result detectFooter(int[] pixels, int width, int height) {
        if (width != WIDTH || height != 180 || pixels.length != width * height)
            return new Result(false, null, null);
        return detectPixels(pixels, 900);
    }
    private Result detectPixels(int[] pixels, int top) {
        boolean[] mask = mask(pixels, 0, top);
        Box sponsor = match(mask, SPONSOR_PATTERN, 78, 475, 110, 502);
        Box phone = match(mask, PHONE_PATTERN, 500, 460, 875, 501);
        if (phone == null && sponsor != null) {
            // Countdown layouts move this button by an odd number of original pixels.
            Box odd = match(mask(pixels, 1, top), PHONE_PATTERN, 500, 460, 875, 501);
            if (odd != null) phone = new Box(odd.left + 1, odd.top, odd.right + 1, odd.bottom);
        }
        return new Result(true, sponsor, phone);
    }
    private static boolean[] mask(int[] pixels, int phase, int top) {
        boolean[] mask = new boolean[960 * 540];
        // Only the known player footer is examined, not titles or recommendations.
        for (int y = 450; y < 530; y++) for (int x = 70; x < 959; x++) {
            boolean bright = false;
            for (int dy = 0; dy < 2; dy++) for (int dx = 0; dx < 2; dx++) {
                int color = pixels[(y * 2 + dy - top) * WIDTH + x * 2 + dx + phase];
                int r = (color >>> 16) & 255, g = (color >>> 8) & 255, b = color & 255;
                bright |= r >= 170 && g >= 170 && b >= 170;
            }
            mask[y * 960 + x] = bright;
        }
        return mask;
    }
    private static final class Pattern {
        final int width, height, on;
        final boolean[] bits;
        final int[] anchors;
        Pattern(String[] rows) {
            width = rows[0].length(); height = rows.length; bits = new boolean[width * height];
            int count = 0;
            for (int y = 0; y < height; y++) {
                if (rows[y].length() != width) throw new IllegalArgumentException("Ragged visual pattern");
                for (int x = 0; x < width; x++) if (rows[y].charAt(x) == '#') {
                    bits[y * width + x] = true; count++;
                }
            }
            on = count; anchors = new int[16];
            int n = 0, next = 0;
            for (int i = 0; i < bits.length && n < anchors.length; i++) if (bits[i]) {
                if (next++ >= n * on / anchors.length) anchors[n++] = i;
            }
        }
    }
    private static Box match(boolean[] mask, Pattern p, int left, int top, int right, int bottom) {
        double best = .86;
        Box found = null;
        for (int y = top; y <= bottom; y++) for (int x = left; x <= right; x++) {
            int anchors = 0;
            for (int a : p.anchors) if (mask[(y + a / p.width) * 960 + x + a % p.width]) anchors++;
            if (anchors < 14) continue;
            int hit = 0, extra = 0;
            for (int py = 0; py < p.height; py++) for (int px = 0; px < p.width; px++) {
                boolean actual = mask[(y + py) * 960 + x + px];
                if (p.bits[py * p.width + px]) { if (actual) hit++; }
                else if (actual) extra++;
            }
            double recall = (double) hit / p.on, precision = (double) hit / Math.max(1, hit + extra);
            double score = Math.min(recall, precision);
            if (score > best) {
                best = score;
                found = new Box(x * 2, y * 2, (x + p.width) * 2, (y + p.height) * 2);
            }
        }
        return found;
    }
}
