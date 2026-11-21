package com.smartemail.writer.app;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Small in-process cache for identical (email, tone) requests.
 * Avoids repeat Gemini calls for the same prompt and keeps v2 latency low on retries.
 */
@Component
public class EmailReplyCache {

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final long ttlMillis;
    private final int maxEntries;

    public EmailReplyCache() {
        this(3600, 500);
    }

    @Autowired
    public EmailReplyCache(@Value("${cache.ttl-seconds:3600}") long ttlSeconds,
                           @Value("${cache.max-entries:500}") int maxEntries) {
        this.ttlMillis = Math.max(1, ttlSeconds) * 1000;
        this.maxEntries = Math.max(1, maxEntries);
    }

    public String get(String key) {
        Entry entry = entries.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.expiresAt().isBefore(Instant.now())) {
            entries.remove(key);
            return null;
        }
        return entry.value();
    }

    public void put(String key, String value) {
        if (entries.size() >= maxEntries) {
            entries.entrySet().stream()
                    .min(Comparator.comparing(e -> e.getValue().expiresAt()))
                    .ifPresent(oldest -> entries.remove(oldest.getKey()));
        }
        entries.put(key, new Entry(value, Instant.now().plusMillis(ttlMillis)));
    }

    public void clear() {
        entries.clear();
    }

    public int size() {
        return entries.size();
    }

    private record Entry(String value, Instant expiresAt) {
    }
}
