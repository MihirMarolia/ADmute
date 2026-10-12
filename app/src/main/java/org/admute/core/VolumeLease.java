package org.admute.core;

/** Owns only the zero it wrote. Never repeatedly fights a user's volume changes. */
public final class VolumeLease {
    public interface Port {
        int volume();
        boolean fixed();
        void setVolume(int value);
        void save(int value);
        int saved();
        void clear();
    }
    private final Port port;
    private boolean active;
    public VolumeLease(Port port) { this.port = port; }
    public void recover() { release(); }
    public void acquire() {
        if (active) return;
        active = true;
        if (port.fixed() || port.volume() == 0) return;
        int original = port.volume();
        port.save(original); // durable before changing device state
        port.setVolume(0);
        if (port.volume() != 0) port.clear();
    }
    public void release() {
        int original = port.saved();
        if (original >= 0 && port.volume() == 0 && !port.fixed()) port.setVolume(original);
        if (original >= 0) port.clear();
        active = false;
    }
}
