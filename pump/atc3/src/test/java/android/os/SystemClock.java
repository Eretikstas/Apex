package android.os;

/**
 * Test copy of the Android clock, found before the stub in `android.jar`.
 *
 * The stub returns 0 for every time and does not sleep. Code that waits until a deadline on this
 * clock then never ends: the time never moves. Most driver tests want the stub (no real waits), so
 * this copy behaves the same by default. The end to end scenarios on the virtual pump talk to the
 * pump like on a phone and need a clock that moves: they run with the system property
 * `atc3.realClock=true` (set by Gradle for `-Patc3VirtualPump=only`), and then this copy uses the
 * real monotonic clock and really sleeps.
 */
public final class SystemClock {

    private SystemClock() {
    }

    private static boolean real() {
        return Boolean.getBoolean("atc3.realClock");
    }

    public static long elapsedRealtime() {
        return real() ? System.nanoTime() / 1_000_000L : 0L;
    }

    public static long elapsedRealtimeNanos() {
        return real() ? System.nanoTime() : 0L;
    }

    public static long uptimeMillis() {
        return real() ? System.nanoTime() / 1_000_000L : 0L;
    }

    public static long currentThreadTimeMillis() {
        return 0L;
    }

    public static boolean setCurrentTimeMillis(long millis) {
        return false;
    }

    public static void sleep(long ms) {
        if (!real() || ms <= 0) return;
        // Like Android: sleep the full time, and keep the interrupt for the caller.
        long end = System.nanoTime() / 1_000_000L + ms;
        boolean interrupted = false;
        long left = ms;
        while (left > 0) {
            try {
                Thread.sleep(left);
            } catch (InterruptedException e) {
                interrupted = true;
            }
            left = end - System.nanoTime() / 1_000_000L;
        }
        if (interrupted) Thread.currentThread().interrupt();
    }
}
