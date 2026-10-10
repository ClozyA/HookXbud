package xyz.fearr.hookxbud.xposed;

/**
 * Bounded, at-most-once activation of an existing, usable skip control.
 */
final class SkipAttempt {
    interface Host {
        boolean isActive();

        boolean canSkip();

        void clickSkip();
    }

    private int remaining;
    private boolean done;

    SkipAttempt() {
        this(40);
    }

    SkipAttempt(int maxChecks) {
        if (maxChecks < 1) throw new IllegalArgumentException("maxChecks must be positive");
        remaining = maxChecks;
    }

    /**
     * Called on the UI thread. True means another delayed check is allowed.
     */
    boolean tick(Host host) {
        if (done) return false;
        if (!host.isActive()) return false;
        if (remaining == 0) {
            done = true;
            return false;
        }
        remaining--;
        if (host.canSkip()) {
            // Set before entering app code, including when its listener throws or re-enters.
            done = true;
            host.clickSkip();
            return false;
        }
        if (remaining == 0) done = true;
        return !done;
    }

    void cancel() {
        done = true;
    }

    boolean isDone() {
        return done;
    }
}
