package org.admute.core;

import java.util.List;
import java.util.regex.Pattern;

/** Selects only explicit, lower-right ad controls from accessibility text or local OCR. */
public final class AdControlDetector {
    public enum Action { DISMISS, SKIP }

    public static final class Candidate {
        private final String text;
        private final int left;
        private final int top;
        private final int right;
        private final int bottom;
        private final boolean clickable;

        public Candidate(String text, int left, int top, int right, int bottom, boolean clickable) {
            this.text = text;
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.clickable = clickable;
        }

        public String text() { return text; }
        public int left() { return left; }
        public int top() { return top; }
        public int right() { return right; }
        public int bottom() { return bottom; }
        public boolean clickable() { return clickable; }
    }

    public static final class Target {
        private final Action action;
        private final Candidate candidate;

        private Target(Action action, Candidate candidate) {
            this.action = action;
            this.candidate = candidate;
        }

        public Action action() { return action; }
        public Candidate candidate() { return candidate; }
    }

    private static final Pattern SPONSORED = Pattern.compile("^sponsored(?:\\s*[·•|]\\s*.+)?$");

    public Target findTarget(String packageName, List<Candidate> candidates, int width, int height) {
        if (!AdDetector.YOUTUBE.equals(packageName) || candidates == null || width <= 0 || height <= 0) return null;

        boolean sponsored = false;
        for (Candidate candidate : candidates) {
            if (candidate != null && validBounds(candidate, width, height) && candidate.text != null
                    && SPONSORED.matcher(AdDetector.normalize(candidate.text)).matches()) {
                sponsored = true;
                break;
            }
        }

        for (Candidate candidate : candidates) {
            if (candidate == null || !candidate.clickable || !inLowerRight(candidate, width, height)) continue;
            String text = AdDetector.normalize(candidate.text);
            if (sponsored && "dismiss".equals(text)) return new Target(Action.DISMISS, candidate);
            if (AdDetector.YOUTUBE.equals(packageName)
                    && ("skip ad".equals(text) || (sponsored && "skip".equals(text))))
                return new Target(Action.SKIP, candidate);
        }
        return null;
    }

    private boolean inLowerRight(Candidate candidate, int width, int height) {
        return validBounds(candidate, width, height)
                && (long) candidate.left + candidate.right >= width
                && (long) candidate.top + candidate.bottom >= (long) height * 6 / 5;
    }

    private boolean validBounds(Candidate candidate, int width, int height) {
        return candidate.left >= 0 && candidate.top >= 0
                && candidate.right > candidate.left && candidate.bottom > candidate.top
                && candidate.right <= width && candidate.bottom <= height;
    }
}
