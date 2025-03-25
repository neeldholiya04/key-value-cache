package com.neel.redis.service;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

class CacheEntry {
    // Use AtomicReference for thread-safe updates to value
    private final AtomicReference<String> value;

    // Use AtomicLong for thread-safe updates to lastAccessTime
    private final AtomicLong lastAccessTime;

    // Record creation time for age-based decisions
    private final long creationTime;

    public CacheEntry(String value) {
        this.value = new AtomicReference<>(value);
        this.lastAccessTime = new AtomicLong(System.nanoTime());
        this.creationTime = System.currentTimeMillis();
    }

    public String getValue() {
        // Atomically update last access time before returning value
        lastAccessTime.set(System.nanoTime());
        return value.get();
    }

    public void setValue(String newValue) {
        // Atomically update both value and last access time
        value.set(newValue);
        lastAccessTime.set(System.nanoTime());
    }

    public long getLastAccessTimeValue() {
        return lastAccessTime.get();
    }

    public long getCreationTime() {
        return creationTime;
    }
}