package org.admute.core;

/** Owns only the zero it wrote. Never repeatedly fights a user's volume changes. */
public final class VolumeLease {
    public interface Port {
        int volume();
        boolean fixed();
        boolean muted();
        void setMuted(boolean muted);
        void setVolume(int value);
        void saveMute();
        boolean ownsMute();
        void clearMute();
        void save(int value);
        int saved();
        void clear();
        default long now() { return System.nanoTime() / 1000000L; }
    }
    private final Port port;
    private boolean active;
    private long restoredAt = -1;
    private long unmutedAt = -1;
    public VolumeLease(Port port) { this.port = port; }
    public boolean recoveryPending() { return port.ownsMute() || port.saved() >= 0; }
    public void recover() { release(); }
    public void acquire() {
        if (active) return;
        // Do not overwrite an unresolved recovery record with a new baseline.
        if (port.ownsMute() || port.saved() >= 0) {
            recover();
            if (port.ownsMute() || port.saved() >= 0) return;
        }
        active = true;
        restoredAt = -1;
        unmutedAt = -1;
        if (port.muted()) return;
        int original = port.volume();
        // TCL/ARC may change its index to zero as a consequence of native mute.
        // Capture the baseline before either audio command, including the native path.
        if (!port.fixed() && original > 0) port.save(original);
        port.saveMute();
        port.setMuted(true);
        if (port.muted()) return;
        if (port.fixed() || port.volume() == 0) return;
        port.setVolume(0);
        // A rejected zero-volume fallback does not cancel the native mute
        // request already sent. Its delayed ARC callbacks still need a baseline.
    }
    public void release() {
        active = false;
        if (port.ownsMute()) {
            if (port.muted() || unmutedAt < 0) {
                port.setMuted(false);
                unmutedAt = -1;
            }
            if (!port.muted()) {
                if (unmutedAt < 0) unmutedAt = port.now();
                if (port.now() - unmutedAt >= 1500) port.clearMute();
            } else unmutedAt = -1;
        }
        int original = port.saved();
        if (original >= 0) {
            if (port.volume() == 0) {
                if (port.fixed()) return;
                port.setVolume(original);
                restoredAt = -1;
                // A silently ignored restore is still pending recovery.
                if (port.volume() == 0) return;
            }
            // HDMI/CEC callbacks can revert an apparently successful restore a
            // few hundred milliseconds later. Keep the durable baseline until
            // its nonzero value has stayed stable across subsequent polls.
            if (port.volume() == original) {
                if (restoredAt < 0) restoredAt = port.now();
                if (port.now() - restoredAt < 1500) { active = false; return; }
            }
            if (port.ownsMute() || port.muted()) return;
            // A different nonzero value belongs to the user and is respected.
            port.clear();
        }
        active = false;
    }
}
