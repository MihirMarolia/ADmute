package org.admute.core;

import java.util.List;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Exact, English player labels only. Real device evidence is required to expand rules. */
public final class AdDetector {
    public static final String YOUTUBE = "com.google.android.youtube.tv";
    public static final String TUBI = "com.tubitv";
    public static final String PLEX = "com.plexapp.android";
    public static final Set<String> PACKAGES = Collections.unmodifiableSet(new java.util.HashSet<>(Arrays.asList(YOUTUBE, TUBI, PLEX)));
    public enum Evidence { AD, CLEAR, UNKNOWN }
    public static final class Label {
        private final String text;
        private final boolean playerControl;
        private final boolean eligible;
        public Label(String text, boolean playerControl) { this(text, playerControl, true); }
        public Label(String text, boolean playerControl, boolean eligible) {
            this.text = text; this.playerControl = playerControl; this.eligible = eligible;
        }
        public String text() { return text; }
        public boolean playerControl() { return playerControl; }
        public boolean eligible() { return eligible; }
    }
    private static final Pattern POD = Pattern.compile("(?:ad|advertisement)\\s+\\d{1,2}\\s+(?:of|/)\\s+\\d{1,2}(?:\\s*[•·:–-]\\s*\\d{1,2}:\\d{2})?");
    private static final Pattern LEFT = Pattern.compile("(?:ads?|advertisements?)\\s+(?:left|remaining)(?:\\s*:?\\s*\\d{1,2}(?::\\d{2})?(?:\\s*(?:s|sec|seconds))?)?");
    private static final Pattern BREAK = Pattern.compile("(?:ad|commercial) break(?:\\s*:?\\s*\\d{1,2}:\\d{2})?");
    private static final Pattern COUNTDOWN = Pattern.compile("(?:ad|advertisement)\\s*[•·:–-]\\s*\\d{1,2}:\\d{2}");
    private static final Pattern SKIP = Pattern.compile("skip ads?(?:\\s+in\\s+\\d{1,2}(?:\\s*(?:s|seconds))?)?");
    public static String normalize(String text) {
        return text == null ? "" : text.replace('\u00a0', ' ').replace("\u200b", "")
                .toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }
    public static boolean meaningful(String text) { return !normalize(text).isEmpty(); }
    /** Diagnostic categories only; never logs titles or arbitrary screen text. */
    public static String marker(String text) {
        String value = normalize(text);
        if (value.equals("sponsored")) return "SPONSORED";
        if (value.equals("send to phone")) return "SEND_TO_PHONE";
        if (POD.matcher(value).matches()) return "AD_POD";
        if (LEFT.matcher(value).matches()) return "AD_REMAINING";
        if (COUNTDOWN.matcher(value).matches()) return "AD_COUNTDOWN";
        if (SKIP.matcher(value).matches()) return "SKIP_AD";
        if (BREAK.matcher(value).matches()) return "AD_BREAK";
        return "";
    }
    public Evidence detect(String pkg, List<Label> labels, boolean complete) {
        if (!PACKAGES.contains(pkg)) return Evidence.CLEAR;
        boolean sponsored = false, sendToPhone = false;
        if (YOUTUBE.equals(pkg)) {
            for (Label label : labels) {
                if (!label.eligible()) continue;
                String value = normalize(label.text());
                sponsored |= value.equals("sponsored");
                sendToPhone |= value.equals("send to phone");
            }
            // User-observed YouTube ad UI pair. Neither label alone qualifies.
            if (sponsored && sendToPhone) return Evidence.AD;
        }
        for (Label label : labels) {
            if (label.text() == null) continue;
            String value = normalize(label.text());
            if (value.length() > 80) continue;
            // A recommendation/title with the same words is not a player control.
            if (!label.playerControl() || !label.eligible()) continue;
            if (POD.matcher(value).matches() || LEFT.matcher(value).matches()
                    || COUNTDOWN.matcher(value).matches()
                    || (YOUTUBE.equals(pkg) && SKIP.matcher(value).matches())
                    || (!YOUTUBE.equals(pkg) && BREAK.matcher(value).matches())) return Evidence.AD;
        }
        return complete ? Evidence.CLEAR : Evidence.UNKNOWN;
    }
}
