package org.admute.core;

/** Monotonic-time state machine, independent of Android. */
public final class AdSession {
    public enum State { IDLE, CANDIDATE, PROTECTED, COOLDOWN }
    public static final long CONFIRM_MS = 300, CLEAR_MS = 1500, UNKNOWN_MS = 8000, MAX_AD_MS = 180000;
    private State state = State.IDLE;
    private String owner = "";
    private long candidateAt, protectedAt, lastAdAt, clearAt = -1;
    public State state() { return state; }
    public boolean protectedNow() { return state == State.PROTECTED; }
    public void reset() { state = State.IDLE; owner = ""; clearAt = -1; }
    public boolean update(String pkg, AdDetector.Evidence evidence, long now, boolean enabled) {
        if (!enabled || !AdDetector.PACKAGES.contains(pkg)) { reset(); return false; }
        if (!owner.equals(pkg)) { reset(); owner = pkg; }
        if (state == State.PROTECTED && now - protectedAt >= MAX_AD_MS) {
            state = State.COOLDOWN; clearAt = -1;
        }
        if (state == State.COOLDOWN) {
            if (evidence == AdDetector.Evidence.CLEAR) {
                if (clearAt < 0) clearAt = now;
                if (now - clearAt >= CLEAR_MS) { state = State.IDLE; clearAt = -1; }
            } else clearAt = -1;
            return false;
        }
        if (evidence == AdDetector.Evidence.AD) {
            lastAdAt = now; clearAt = -1;
            if (state == State.IDLE) { state = State.CANDIDATE; candidateAt = now; }
            else if (state == State.CANDIDATE && now - candidateAt >= CONFIRM_MS) {
                state = State.PROTECTED; protectedAt = now;
            }
        } else if (state == State.CANDIDATE) {
            state = State.IDLE;
        } else if (state == State.PROTECTED) {
            if (evidence == AdDetector.Evidence.CLEAR) {
                if (clearAt < 0) clearAt = now;
                if (now - clearAt >= CLEAR_MS) { state = State.IDLE; clearAt = -1; }
            } else {
                clearAt = -1;
                if (now - lastAdAt >= UNKNOWN_MS) { state = State.COOLDOWN; clearAt = -1; }
            }
        }
        return protectedNow();
    }
}
