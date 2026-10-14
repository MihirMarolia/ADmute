package org.admute.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class VolumeLeaseTest {
    static class Device implements VolumeLease.Port {
        int current = 12, saved = -1, writes;
        boolean fixed, ignoresSet, muteSupported, muted, savedMute, ignoresMute, delayMute;
        public int volume() { return current; }
        public boolean fixed() { return fixed; }
        public boolean muted() { return muted; }
        public void setMuted(boolean value) {
            if (muteSupported && !ignoresMute && !(delayMute && value)) muted = value;
        }
        public void setVolume(int value) { writes++; if (!ignoresSet) current = value; }
        public void saveMute() { savedMute = true; }
        public boolean ownsMute() { return savedMute; }
        public void clearMute() { savedMute = false; }
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
        Device d = new Device(); d.muted = true; VolumeLease lease = new VolumeLease(d);
        lease.acquire(); lease.release(); assertEquals(12, d.current); assertEquals(0, d.writes);
        assertTrue(d.muted);
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
        lease.acquire(); assertEquals(-1, d.saved); assertTrue(d.savedMute);
        lease.release(); assertEquals(1, d.writes); assertFalse(d.savedMute);
    }
    @Test public void releaseFailurePreservesRecoveryRecord() {
        Device d = new Device() {
            public void setVolume(int value) { if (value > 0) throw new SecurityException(); super.setVolume(value); }
        };
        VolumeLease lease = new VolumeLease(d); lease.acquire();
        try { lease.release(); fail("Expected failure"); } catch (SecurityException expected) { assertEquals(12, d.saved); }
    }
    @Test public void nativeMuteRestoresMuteWithoutChangingVolume() {
        Device d = new Device(); d.muteSupported = true; VolumeLease lease = new VolumeLease(d);
        lease.acquire(); lease.acquire();
        assertTrue(d.muted); assertTrue(d.savedMute); assertEquals(12, d.current); assertEquals(0, d.writes);
        lease.release();
        assertFalse(d.muted); assertFalse(d.savedMute); assertEquals(12, d.current);
    }
    @Test public void nativeMutePreservesMuteThatWasAlreadyOn() {
        Device d = new Device(); d.muted = true; d.muteSupported = true;
        new VolumeLease(d).acquire(); new VolumeLease(d).release();
        assertTrue(d.muted); assertFalse(d.savedMute);
    }
    @Test public void nativeMuteRecoveryAfterRestartUnmutesOnlyOwnedMute() {
        Device d = new Device(); d.muteSupported = true;
        new VolumeLease(d).acquire(); new VolumeLease(d).recover();
        assertFalse(d.muted); assertFalse(d.savedMute);
    }
    @Test public void nativeMuteFallbackUsesVolumeWhenMuteCommandIsIgnored() {
        Device d = new Device(); d.ignoresMute = true; VolumeLease lease = new VolumeLease(d);
        lease.acquire();
        assertEquals(0, d.current); assertEquals(12, d.saved); assertTrue(d.savedMute);
        lease.release(); assertEquals(12, d.current);
    }
    @Test public void userUnmuteDuringNativeLeaseIsNotOverridden() {
        Device d = new Device(); d.muteSupported = true; VolumeLease lease = new VolumeLease(d);
        lease.acquire(); d.muted = false; lease.release();
        assertFalse(d.muted); assertEquals(12, d.current);
    }
    @Test public void delayedNativeMuteIsReleasedEvenAfterVolumeFallback() {
        Device d = new Device(); d.muteSupported = true; d.delayMute = true;
        VolumeLease lease = new VolumeLease(d);
        lease.acquire();
        d.delayMute = false;
        d.muted = true;
        lease.release();
        assertFalse(d.muted); assertFalse(d.savedMute); assertEquals(12, d.current);
    }
    @Test public void ignoredRestoreKeepsBaselineUntilRecoverySucceeds() {
        Device d = new Device(); VolumeLease lease = new VolumeLease(d);
        lease.acquire(); d.ignoresSet = true;
        lease.release();
        assertEquals(0, d.current); assertEquals(12, d.saved);
        // A new lease must not replace the saved baseline with zero.
        new VolumeLease(d).acquire(); assertEquals(12, d.saved);
        d.ignoresSet = false;
        new VolumeLease(d).recover();
        assertEquals(12, d.current); assertEquals(-1, d.saved);
    }
    @Test public void fixedRouteKeepsPendingBaselineUntilRouteAllowsRecovery() {
        Device d = new Device(); VolumeLease lease = new VolumeLease(d);
        lease.acquire(); d.fixed = true;
        lease.release(); assertEquals(12, d.saved); assertEquals(0, d.current);
        d.fixed = false; lease.recover();
        assertEquals(12, d.current); assertEquals(-1, d.saved);
    }
    @Test public void failedUnmuteIsRetriedBeforeStartingAnotherLease() {
        Device d = new Device(); d.muteSupported = true;
        new VolumeLease(d).acquire(); d.ignoresMute = true;
        VolumeLease restarted = new VolumeLease(d);
        restarted.recover(); restarted.acquire();
        assertTrue(d.savedMute); assertTrue(d.muted);
        d.ignoresMute = false; restarted.recover();
        assertFalse(d.savedMute); assertFalse(d.muted);
    }
}
