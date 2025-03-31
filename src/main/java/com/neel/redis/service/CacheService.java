package com.neel.redis.service;

import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Service
public class CacheService {
    private static final int MAX_ENTRIES = 5_000_000;
    private static final double MEMORY_THRESHOLD = 0.7;
    private static final int EVICTION_BATCH_SIZE = 5000;
    private static final long MEMORY_CHECK_INTERVAL_MS = 1000;

    private static final int SHARD_COUNT = 512;

    private final AtomicLong totalGets = new AtomicLong(0);
    private final AtomicLong totalPuts = new AtomicLong(0);
    private final AtomicLong totalCacheMisses = new AtomicLong(0);

    private final ConcurrentHashMap<String, CacheEntry>[] cacheShards = new ConcurrentHashMap[SHARD_COUNT];
    private final AtomicLong cacheSize = new AtomicLong(0);
    private final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final ReadWriteLock[] shardLocks = new ReentrantReadWriteLock[SHARD_COUNT];

    private final ReadWriteLock evictionLock = new ReentrantReadWriteLock();

    public CacheService() {
        for (int i = 0; i < SHARD_COUNT; i++) {
            cacheShards[i] = new ConcurrentHashMap<>(MAX_ENTRIES / SHARD_COUNT, 0.75f, 32);
            shardLocks[i] = new ReentrantReadWriteLock();
        }

        scheduler.scheduleAtFixedRate(this::checkMemoryUsage,
                MEMORY_CHECK_INTERVAL_MS, MEMORY_CHECK_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    public Map<String, Object> put(String key, String value) {
        totalPuts.incrementAndGet();

        if (key == null || value == null) {
            return Map.of(
                    "status", "ERROR",
                    "message", "Key and value must not be null."
            );
        }

        if (key.length() > 256 || value.length() > 256) {
            return Map.of(
                    "status", "ERROR",
                    "message", "Key and value must be <= 256 characters."
            );
        }

        int shardIndex = getShardIndex(key);
        ConcurrentHashMap<String, CacheEntry> shard = cacheShards[shardIndex];
        ReadWriteLock shardLock = shardLocks[shardIndex];

        shardLock.readLock().lock();
        try {
            evictionLock.readLock().lock();
            try {
                CacheEntry newEntry = new CacheEntry(value);
                CacheEntry existing = shard.putIfAbsent(key, newEntry);

                if (existing != null) {
                    existing.setValue(value);
                } else {
                    cacheSize.incrementAndGet();
                }
            } finally {
                evictionLock.readLock().unlock();
            }
        } finally {
            shardLock.readLock().unlock();
        }

        return Map.of(
                "status", "OK",
                "message", "Key inserted/updated successfully."
        );
    }

    public Map<String, Object> get(String key) {
        totalGets.incrementAndGet();

        if (key == null) {
            return Map.of(
                    "status", "ERROR",
                    "message", "Key must not be null."
            );
        }

        if (key.length() > 256) {
            return Map.of(
                    "status", "ERROR",
                    "message", "Key must be <= 256 characters."
            );
        }

        int shardIndex = getShardIndex(key);
        ConcurrentHashMap<String, CacheEntry> shard = cacheShards[shardIndex];
        ReadWriteLock shardLock = shardLocks[shardIndex];

        shardLock.readLock().lock();
        try {
            evictionLock.readLock().lock();
            try {
                CacheEntry entry = shard.get(key);
                if (entry == null) {
                    totalCacheMisses.incrementAndGet();
                    return Map.of(
                            "status", "ERROR",
                            "message", "Key not found."
                    );
                }

                return Map.of(
                        "status", "OK",
                        "key", key,
                        "value", entry.getValue()
                );
            } finally {
                evictionLock.readLock().unlock();
            }
        } finally {
            shardLock.readLock().unlock();
        }
    }

    private int getShardIndex(String key) {
        return Math.abs(key.hashCode() % SHARD_COUNT);
    }

    private void checkMemoryUsage() {
        long usedMemory = memoryBean.getHeapMemoryUsage().getUsed();
        long maxMemory = memoryBean.getHeapMemoryUsage().getMax();
        double memoryUsageRatio = (double) usedMemory / maxMemory;

        if (memoryUsageRatio > MEMORY_THRESHOLD || cacheSize.get() > MAX_ENTRIES) {
            evictLeastRecentlyUsed();
        }
    }

    private void evictLeastRecentlyUsed() {
        evictionLock.writeLock().lock();
        try {
            PriorityQueue<EvictionCandidate> candidates = new PriorityQueue<>(
                EVICTION_BATCH_SIZE * 2,
                (e1, e2) -> Long.compare(e1.accessTime, e2.accessTime)
            );

            for (int shardIndex = 0; shardIndex < SHARD_COUNT; shardIndex++) {
                ReadWriteLock shardLock = shardLocks[shardIndex];
                ConcurrentHashMap<String, CacheEntry> shard = cacheShards[shardIndex];

                shardLock.readLock().lock();
                try {
                    int finalShardIndex = shardIndex;
                    shard.forEach((key, entry) -> {
                        candidates.offer(new EvictionCandidate(
                            key, entry.getLastAccessTimeValue(), finalShardIndex
                        ));

                        if (candidates.size() > EVICTION_BATCH_SIZE * 2) {
                            candidates.poll();
                        }
                    });
                } finally {
                    shardLock.readLock().unlock();
                }
            }

            int evictedCount = 0;
            while (!candidates.isEmpty() && evictedCount < EVICTION_BATCH_SIZE) {
                EvictionCandidate candidate = candidates.poll();

                int shardIndex = candidate.shardIndex;
                ConcurrentHashMap<String, CacheEntry> shard = cacheShards[shardIndex];
                ReadWriteLock shardLock = shardLocks[shardIndex];

                shardLock.writeLock().lock();
                try {
                    if (shard.remove(candidate.key) != null) {
                        cacheSize.decrementAndGet();
                        evictedCount++;
                    }
                } finally {
                    shardLock.writeLock().unlock();
                }
            }

            System.out.println("Cache eviction: removed " + evictedCount +
                               " entries, current size: " + cacheSize.get() +
                               ", memory usage: " +
                               (memoryBean.getHeapMemoryUsage().getUsed() * 100.0 /
                                memoryBean.getHeapMemoryUsage().getMax()) + "%");
        } catch (Exception e) {
            System.err.println("Error during cache eviction: " + e.getMessage());
            e.printStackTrace();
        } finally {
            evictionLock.writeLock().unlock();
        }
    }


    private static class EvictionCandidate {
        final String key;
        final long accessTime;
        final int shardIndex;

        EvictionCandidate(String key, long accessTime, int shardIndex) {
            this.key = key;
            this.accessTime = accessTime;
            this.shardIndex = shardIndex;
        }
    }
}