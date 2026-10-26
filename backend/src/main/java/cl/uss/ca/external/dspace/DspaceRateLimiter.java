package cl.uss.ca.external.dspace;

public class DspaceRateLimiter {
    private final long minIntervalMs;
    private long nextAllowedAt;

    public DspaceRateLimiter(long minIntervalMs) {
        this.minIntervalMs = Math.max(0L, minIntervalMs);
    }

    public synchronized void acquire() {
        if (minIntervalMs <= 0L) {
            return;
        }
        long now = System.currentTimeMillis();
        long wait = nextAllowedAt - now;
        if (wait > 0L) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new cl.uss.ca.exception.ValidationException("DSPACE_INTERRUPTED", "DSpace rate limiter was interrupted");
            }
            now = System.currentTimeMillis();
        }
        nextAllowedAt = now + minIntervalMs;
    }
}
