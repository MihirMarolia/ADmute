package org.admute.core;

/** Updated only by distinct, fresh screenshots. Capture failures never count as clear frames. */
public final class VisualAdSession {
    private boolean active;
    private long candidateAt = -1, lastFrame = -1, clearAt = -1;
    private int clearFrames;
    public boolean active() { return active; }
    public void reset() { active = false; candidateAt = lastFrame = clearAt = -1; clearFrames = 0; }
    public boolean observe(boolean pair, boolean sponsor, long capturedAt) {
        if (capturedAt <= lastFrame) return active;
        long previous = lastFrame;
        lastFrame = capturedAt;
        if (!active) {
            if (!pair) { candidateAt = -1; return false; }
            if (candidateAt < 0 || capturedAt - previous > 2000) candidateAt = capturedAt;
            else if (capturedAt - candidateAt >= 300) active = true;
        } else if (sponsor || pair) {
            clearAt = -1; clearFrames = 0;
        } else {
            // Require consecutive successful reads spanning at least 1.5 seconds.
            if (clearAt < 0 || capturedAt - previous > 2000) { clearAt = capturedAt; clearFrames = 0; }
            clearFrames++;
            if (clearFrames >= 3 && capturedAt - clearAt >= 1500) reset();
        }
        return active;
    }
    public void failed() { candidateAt = clearAt = -1; clearFrames = 0; }
}
