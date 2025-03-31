package com.neel.redis.service;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

class CacheEntry {
    private final AtomicReference<String> value;

    private final AtomicLong lastAccessTime;

    private final long creationTime;

    public CacheEntry(String value) {
        this.value = new AtomicReference<>(value);
        this.lastAccessTime = new AtomicLong(System.nanoTime());
        this.creationTime = System.currentTimeMillis();
    }

    public String getValue() {
        lastAccessTime.set(System.nanoTime());
        return value.get();
    }

    public void setValue(String newValue) {
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