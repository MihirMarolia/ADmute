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
    }
    private final Port port;
    private boolean active;
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
        if (port.muted()) return;
        port.saveMute();
        port.setMuted(true);
        if (port.muted()) return;
        if (port.fixed() || port.volume() == 0) return;
        int original = port.volume();
        port.save(original); // durable before changing device state
        port.setVolume(0);
        if (port.volume() != 0) port.clear();
    }
    public void release() {
        if (port.ownsMute()) {
            port.setMuted(false);
            if (!port.muted()) port.clearMute();
        }
        int original = port.saved();
        if (original >= 0) {
            if (port.volume() == 0) {
                if (port.fixed()) return;
                port.setVolume(original);
                // A silently ignored restore is still pending recovery.
                if (port.volume() == 0) return;
            }
            port.clear();
        }
        active = false;
    }
}
