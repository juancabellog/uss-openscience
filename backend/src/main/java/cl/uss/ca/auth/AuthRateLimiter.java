package cl.uss.ca.auth;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class AuthRateLimiter {
    private final Map<String, List<Long>> attempts = new ConcurrentHashMap<>();

    public boolean allow(String key, int max, long windowMs) {
        if (max <= 0) {
            return true;
        }
        long now = System.currentTimeMillis();
        List<Long> times = attempts.computeIfAbsent(key, ignored -> new ArrayList<>());
        synchronized (times) {
            Iterator<Long> it = times.iterator();
            while (it.hasNext()) {
                if (now - it.next() > windowMs) {
                    it.remove();
                }
            }
            if (times.size() >= max) {
                return false;
            }
            times.add(now);
            return true;
        }
    }
}
