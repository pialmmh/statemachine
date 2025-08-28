package com.telcobright.statemachine.pool;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * High-performance object pool manager for telecom realtime processing
 * Minimizes GC pressure by reusing objects instead of constant allocation
 */
public class ObjectPoolManager<T extends Poolable> {
    
    private final ConcurrentLinkedQueue<T> available;
    private final AtomicInteger totalCreated;
    private final AtomicInteger totalBorrowed;
    private final AtomicInteger totalReturned;
    private final int maxPoolSize;
    private final Supplier<T> objectFactory;
    private final String poolName;
    
    public ObjectPoolManager(String poolName, Supplier<T> objectFactory, int maxPoolSize) {
        this.poolName = poolName;
        this.objectFactory = objectFactory;
        this.maxPoolSize = maxPoolSize;
        this.available = new ConcurrentLinkedQueue<>();
        this.totalCreated = new AtomicInteger(0);
        this.totalBorrowed = new AtomicInteger(0);
        this.totalReturned = new AtomicInteger(0);
        
        // Pre-warm the pool with initial objects
        prewarmPool(Math.min(maxPoolSize / 4, 100));
    }
    
    private void prewarmPool(int initialSize) {
        for (int i = 0; i < initialSize; i++) {
            T obj = objectFactory.get();
            obj.resetForReuse();
            available.offer(obj);
            totalCreated.incrementAndGet();
        }
    }
    
    /**
     * Borrow an object from the pool
     * @return pooled object or new instance if pool is empty
     */
    public T borrow() {
        T obj = available.poll();
        if (obj == null) {
            // Create new object if under limit
            if (totalCreated.get() < maxPoolSize) {
                obj = objectFactory.get();
                totalCreated.incrementAndGet();
            } else {
                // Pool exhausted, create temporary object
                obj = objectFactory.get();
            }
        }
        totalBorrowed.incrementAndGet();
        return obj;
    }
    
    /**
     * Return object to pool for reuse
     * @param obj object to return
     */
    public void returnObject(T obj) {
        if (obj != null) {
            try {
                obj.resetForReuse();
                available.offer(obj);
                totalReturned.incrementAndGet();
            } catch (Exception e) {
                // If reset fails, don't return to pool
                System.err.println("Failed to reset pooled object: " + e.getMessage());
            }
        }
    }
    
    /**
     * Get pool statistics
     */
    public PoolStatistics getStatistics() {
        return new PoolStatistics(
            poolName,
            available.size(),
            totalCreated.get(),
            totalBorrowed.get(),
            totalReturned.get(),
            maxPoolSize
        );
    }
    
    /**
     * Clear pool and reset counters
     */
    public void clear() {
        available.clear();
        totalCreated.set(0);
        totalBorrowed.set(0);
        totalReturned.set(0);
    }
    
    public static class PoolStatistics {
        public final String poolName;
        public final int available;
        public final int totalCreated;
        public final int totalBorrowed;
        public final int totalReturned;
        public final int maxSize;
        public final double hitRatio;
        
        public PoolStatistics(String poolName, int available, int totalCreated, 
                            int totalBorrowed, int totalReturned, int maxSize) {
            this.poolName = poolName;
            this.available = available;
            this.totalCreated = totalCreated;
            this.totalBorrowed = totalBorrowed;
            this.totalReturned = totalReturned;
            this.maxSize = maxSize;
            this.hitRatio = totalBorrowed > 0 ? 
                (double)(totalBorrowed - totalCreated) / totalBorrowed : 0.0;
        }
        
        @Override
        public String toString() {
            return String.format("Pool[%s]: available=%d, created=%d, borrowed=%d, returned=%d, hitRatio=%.2f%%",
                poolName, available, totalCreated, totalBorrowed, totalReturned, hitRatio * 100);
        }
    }
}