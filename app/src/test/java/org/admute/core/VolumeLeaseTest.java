package org.admute.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class VolumeLeaseTest {
    static class Device implements VolumeLease.Port {
        int current = 12, saved = -1, writes;
        boolean fixed, ignoresSet;
        public int volume() { return current; }
        public boolean fixed() { return fixed; }
        public void setVolume(int value) { writes++; if (!ignoresSet) current = value; }
        public void save(int value) { saved = value; }
        public int saved() { return saved; }
        public void clear() { saved = -1; }
    }
    @Test public void mutesOnceAndRestoresExactOriginal() {
        Device d = new Device(); VolumeLease lease = new VolumeLease(d);
        lease.acquire(); lease.acquire(); assertEquals(0, d.current); assertEquals(12, d.saved); assertEquals(1, d.writes);
        lease.release(); lease.release(); assertEquals(12, d.current); assertEquals(-1, d.saved); assertEquals(2, d.writes);
    }
    @Test public void doesNotOverrideUserVolumeChange() {
        Device d = new Device(); VolumeLease lease = new VolumeLease(d);
        lease.acquire(); d.current = 5; lease.acquire(); lease.release(); assertEquals(5, d.current);
    }
    @Test public void alreadyMutedStaysMuted() {
        Device d = new Device(); d.current = 0; VolumeLease lease = new VolumeLease(d);
        lease.acquire(); lease.release(); assertEquals(0, d.current); assertEquals(0, d.writes);
    }
    @Test public void fixedVolumeIsLeftAlone() {
        Device d = new Device(); d.fixed = true; VolumeLease lease = new VolumeLease(d);
        lease.acquire(); lease.release(); assertEquals(12, d.current); assertEquals(0, d.writes);
    }
    @Test public void recoversAfterRestartButRespectsNewVolume() {
        Device d = new Device(); new VolumeLease(d).acquire(); new VolumeLease(d).recover(); assertEquals(12, d.current);
        new VolumeLease(d).acquire(); d.current = 6; new VolumeLease(d).recover(); assertEquals(6, d.current);
    }
    @Test public void silentlyIgnoredVolumeCommandDoesNotClaimOwnership() {
        Device d = new Device(); d.ignoresSet = true; VolumeLease lease = new VolumeLease(d);
        lease.acquire(); assertEquals(-1, d.saved); lease.release(); assertEquals(1, d.writes);
    }
    @Test public void releaseFailurePreservesRecoveryRecord() {
        Device d = new Device() {
            public void setVolume(int value) { if (value > 0) throw new SecurityException(); super.setVolume(value); }
        };
        VolumeLease lease = new VolumeLease(d); lease.acquire();
        try { lease.release(); fail("Expected failure"); } catch (SecurityException expected) { assertEquals(12, d.saved); }
    }
}
