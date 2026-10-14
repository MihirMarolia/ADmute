package org.admute.core;

/** Main-thread guard for delayed screen reads and duplicate control activation. */
public final class AdControlGuard {
    public enum Source { TREE, OCR }
    public static final long MAX_SCAN_AGE_MS = 2500;
    private long generation;
    private String lastAction = "";
    private int clearScans;
    private Source actionSource = Source.TREE;

    public long generation() { return generation; }
    public void invalidate() { generation++; }
    public void reset() { invalidate(); lastAction = ""; clearScans = 0; }
    public boolean current(long token, long startedAt, long now) {
        return token == generation && now >= startedAt && now - startedAt <= MAX_SCAN_AGE_MS;
    }
    public boolean canActivate(String key) { return !key.equals(lastAction); }
    public void activated(String key) { activated(key, Source.TREE); }
    public void activated(String key, Source source) {
        lastAction = key; clearScans = 0; actionSource = source;
    }
    public void observe(boolean hasControl) { observe(hasControl, Source.TREE); }
    public void observe(boolean hasControl, Source source) {
        // An inaccessible tree cannot prove that an OCR-only control disappeared.
        if (!lastAction.isEmpty() && source != actionSource) return;
        if (hasControl) clearScans = 0;
        else if (++clearScans >= 2) lastAction = "";
    }
}
