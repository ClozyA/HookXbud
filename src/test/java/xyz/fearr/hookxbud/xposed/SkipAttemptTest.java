package xyz.fearr.hookxbud.xposed;

import org.junit.Test;
import static org.junit.Assert.*;

public final class SkipAttemptTest {
    @Test public void absentOrUnusableButtonIsNeverClickedAndPollingIsBounded() {
        Host host = new Host();
        SkipAttempt attempt = new SkipAttempt();
        int polls = 0;
        do { polls++; } while (attempt.tick(host));
        assertEquals(40, polls);
        host.ready = true;
        assertFalse(attempt.tick(host));
        assertEquals(0, host.clicks);
    }

    @Test public void pauseDoesNotClickAndResumeCanActivateOnlyOnce() {
        Host host = new Host();
        host.ready = true;
        host.active = false;
        SkipAttempt attempt = new SkipAttempt();
        assertFalse(attempt.tick(host));
        assertEquals(0, host.clicks);
        host.active = true;
        assertFalse(attempt.tick(host));
        assertFalse(attempt.tick(host));
        assertEquals(1, host.clicks);
    }

    @Test public void cancellationPreventsLaterCallback() {
        Host host = new Host();
        SkipAttempt attempt = new SkipAttempt();
        assertTrue(attempt.tick(host));
        attempt.cancel();
        host.ready = true;
        assertFalse(attempt.tick(host));
        assertEquals(0, host.clicks);
    }

    @Test public void appListenerFailureIsNotRetried() {
        Host host = new Host();
        host.ready = true;
        host.fail = true;
        SkipAttempt attempt = new SkipAttempt();
        assertThrows(IllegalStateException.class, () -> attempt.tick(host));
        assertFalse(attempt.tick(host));
        assertEquals(1, host.clicks);
    }

    @Test public void listenerReentryCannotActivateTwice() {
        SkipAttempt attempt = new SkipAttempt();
        int[] clicks = {0};
        SkipAttempt.Host host = new SkipAttempt.Host() {
            @Override public boolean isActive() { return true; }
            @Override public boolean canSkip() { return true; }
            @Override public void clickSkip() {
                clicks[0]++;
                assertFalse(attempt.tick(this));
            }
        };
        assertFalse(attempt.tick(host));
        assertEquals(1, clicks[0]);
    }

    @Test public void delayedInterstitialCloseCanBecomeUsableAfterTheSplashBudget() {
        Host host = new Host();
        SkipAttempt attempt = new SkipAttempt(XbudTargets.autoButton(XbudTargets.PORTRAIT).maxChecks());
        for (int check = 0; check < 80; check++) assertTrue(attempt.tick(host));
        assertEquals(0, host.clicks);
        host.ready = true;
        assertFalse(attempt.tick(host));
        assertTrue(attempt.isDone());
        assertFalse(attempt.tick(host));
        assertEquals(1, host.clicks);
    }

    private static final class Host implements SkipAttempt.Host {
        boolean active = true;
        boolean ready;
        boolean fail;
        int clicks;
        @Override public boolean isActive() { return active; }
        @Override public boolean canSkip() { return ready; }
        @Override public void clickSkip() {
            clicks++;
            if (fail) throw new IllegalStateException("app listener failed");
        }
    }
}
