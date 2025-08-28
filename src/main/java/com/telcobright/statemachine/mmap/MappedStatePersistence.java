package com.telcobright.statemachine.mmap;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * High-performance memory-mapped state persistence for telecom realtime processing
 * Uses memory-mapped files for ultra-fast state updates and reads
 */
public class MappedStatePersistence {
    
    // Record layout: [machineId_hash(8)] [state_enum(1)] [timestamp(8)] [call_data(83)] = 100 bytes total
    private static final int RECORD_SIZE = 100;
    private static final int MACHINE_ID_HASH_OFFSET = 0;
    private static final int STATE_OFFSET = 8;
    private static final int TIMESTAMP_OFFSET = 9;
    private static final int CALL_DATA_OFFSET = 17;
    private static final int CALL_DATA_SIZE = 83;
    
    // State enumeration for compact storage
    public enum CallState {
        IDLE(0), RINGING(1), CONNECTED(2), DISCONNECTED(3), BUSY(4), FAILED(5), TIMEOUT(6);
        
        private final byte value;
        CallState(int value) { this.value = (byte) value; }
        public byte getValue() { return value; }
        
        private static final CallState[] VALUES = values();
        public static CallState fromByte(byte b) {
            return b >= 0 && b < VALUES.length ? VALUES[b] : IDLE;
        }
    }
    
    // Memory-mapped file
    private final MappedByteBuffer mappedBuffer;
    private final RandomAccessFile backingFile;
    private final FileChannel fileChannel;
    private final Path filePath;
    
    // Machine ID to record index mapping
    private final ConcurrentHashMap<String, Integer> machineIdToIndex = new ConcurrentHashMap<>();
    private final AtomicInteger nextAvailableIndex = new AtomicInteger(0);
    private final int maxMachines;
    
    // Thread safety
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    
    // Statistics
    private final AtomicInteger totalReads = new AtomicInteger(0);
    private final AtomicInteger totalWrites = new AtomicInteger(0);
    private final AtomicInteger cacheHits = new AtomicInteger(0);
    
    public MappedStatePersistence(String filePath, int maxMachines) throws IOException {
        this.maxMachines = maxMachines;
        this.filePath = Paths.get(filePath);
        
        // Ensure directory exists
        Files.createDirectories(this.filePath.getParent());
        
        // Calculate file size
        long fileSize = (long) maxMachines * RECORD_SIZE;
        
        // Create backing file
        this.backingFile = new RandomAccessFile(filePath, "rw");
        this.backingFile.setLength(fileSize);
        
        // Create memory mapping
        this.fileChannel = backingFile.getChannel();
        this.mappedBuffer = fileChannel.map(FileChannel.MapMode.READ_WRITE, 0, fileSize);
        
        // Pre-allocate all records with empty state
        initializeEmptyRecords();
        
        System.out.println("[MappedStatePersistence] Initialized with " + maxMachines + 
                          " machine slots (" + (fileSize / 1024 / 1024) + " MB)");
    }
    
    private void initializeEmptyRecords() {
        ByteBuffer buffer = ByteBuffer.allocate(RECORD_SIZE);
        
        for (int i = 0; i < maxMachines; i++) {
            buffer.clear();
            buffer.putLong(0); // Empty machine ID hash
            buffer.put(CallState.IDLE.getValue());
            buffer.putLong(0); // Timestamp 0
            buffer.put(new byte[CALL_DATA_SIZE]); // Empty call data
            
            mappedBuffer.position(i * RECORD_SIZE);
            mappedBuffer.put(buffer.array());
        }
        
        mappedBuffer.force(); // Ensure data is written
    }
    
    /**
     * Update machine state - ultra-fast O(1) operation
     */
    public boolean updateMachineState(String machineId, CallState state, long timestamp, CallData callData) {
        if (machineId == null || state == null) {
            return false;
        }
        
        rwLock.writeLock().lock();
        try {
            // Get or allocate record index
            Integer index = machineIdToIndex.get(machineId);
            if (index == null) {
                index = nextAvailableIndex.getAndIncrement();
                if (index >= maxMachines) {
                    System.err.println("Maximum machines exceeded: " + maxMachines);
                    return false;
                }
                machineIdToIndex.put(machineId, index);
            } else {
                cacheHits.incrementAndGet();
            }
            
            // Calculate record offset
            int recordOffset = index * RECORD_SIZE;
            
            // Update record in memory-mapped buffer
            mappedBuffer.position(recordOffset + MACHINE_ID_HASH_OFFSET);
            mappedBuffer.putLong(machineId.hashCode()); // Store hash for verification
            
            mappedBuffer.position(recordOffset + STATE_OFFSET);
            mappedBuffer.put(state.getValue());
            
            mappedBuffer.position(recordOffset + TIMESTAMP_OFFSET);
            mappedBuffer.putLong(timestamp);
            
            // Write call data
            if (callData != null) {
                mappedBuffer.position(recordOffset + CALL_DATA_OFFSET);
                mappedBuffer.put(callData.serialize());
            }
            
            totalWrites.incrementAndGet();
            return true;
            
        } finally {
            rwLock.writeLock().unlock();
        }
    }
    
    /**
     * Read machine state - ultra-fast O(1) operation
     */
    public MachineStateSnapshot readMachineState(String machineId) {
        if (machineId == null) {
            return null;
        }
        
        rwLock.readLock().lock();
        try {
            Integer index = machineIdToIndex.get(machineId);
            if (index == null) {
                return null;
            }
            
            int recordOffset = index * RECORD_SIZE;
            
            // Read state
            mappedBuffer.position(recordOffset + STATE_OFFSET);
            CallState state = CallState.fromByte(mappedBuffer.get());
            
            // Read timestamp
            mappedBuffer.position(recordOffset + TIMESTAMP_OFFSET);
            long timestamp = mappedBuffer.getLong();
            
            // Read call data
            byte[] callDataBytes = new byte[CALL_DATA_SIZE];
            mappedBuffer.position(recordOffset + CALL_DATA_OFFSET);
            mappedBuffer.get(callDataBytes);
            CallData callData = CallData.deserialize(callDataBytes);
            
            totalReads.incrementAndGet();
            
            return new MachineStateSnapshot(machineId, state, timestamp, callData);
            
        } finally {
            rwLock.readLock().unlock();
        }
    }
    
    /**
     * Batch read multiple machine states
     */
    public MachineStateSnapshot[] readMachineStates(String[] machineIds) {
        MachineStateSnapshot[] results = new MachineStateSnapshot[machineIds.length];
        
        rwLock.readLock().lock();
        try {
            for (int i = 0; i < machineIds.length; i++) {
                results[i] = readMachineState(machineIds[i]);
            }
        } finally {
            rwLock.readLock().unlock();
        }
        
        return results;
    }
    
    /**
     * Force sync to disk
     */
    public void sync() {
        rwLock.readLock().lock();
        try {
            mappedBuffer.force();
        } finally {
            rwLock.readLock().unlock();
        }
    }
    
    /**
     * Get persistence statistics
     */
    public String getStatistics() {
        return String.format(
            "MappedStatePersistence: machines=%d/%d, reads=%d, writes=%d, cache_hits=%d, hit_ratio=%.2f%%",
            machineIdToIndex.size(), maxMachines, 
            totalReads.get(), totalWrites.get(), cacheHits.get(),
            totalReads.get() > 0 ? (cacheHits.get() * 100.0) / totalReads.get() : 0.0
        );
    }
    
    /**
     * Close and cleanup resources
     */
    public void close() throws IOException {
        rwLock.writeLock().lock();
        try {
            sync(); // Final sync
            fileChannel.close();
            backingFile.close();
        } finally {
            rwLock.writeLock().unlock();
        }
    }
    
    /**
     * Compact call data for memory-mapped storage
     */
    public static class CallData {
        private String callerId;    // Max 20 chars
        private String calleeId;    // Max 20 chars
        private int ringCount;      // 4 bytes
        private long ringDuration;  // 8 bytes
        private float billingAmount; // 4 bytes
        private short callQuality;   // 2 bytes (0-100)
        
        public CallData(String callerId, String calleeId, int ringCount, 
                       long ringDuration, float billingAmount, short callQuality) {
            this.callerId = callerId != null ? callerId.substring(0, Math.min(callerId.length(), 20)) : "";
            this.calleeId = calleeId != null ? calleeId.substring(0, Math.min(calleeId.length(), 20)) : "";
            this.ringCount = ringCount;
            this.ringDuration = ringDuration;
            this.billingAmount = billingAmount;
            this.callQuality = callQuality;
        }
        
        public byte[] serialize() {
            ByteBuffer buffer = ByteBuffer.allocate(CALL_DATA_SIZE);
            
            // Caller ID (20 bytes)
            byte[] callerBytes = new byte[20];
            byte[] callerIdBytes = callerId.getBytes();
            System.arraycopy(callerIdBytes, 0, callerBytes, 0, Math.min(callerIdBytes.length, 20));
            buffer.put(callerBytes);
            
            // Callee ID (20 bytes)
            byte[] calleeBytes = new byte[20];
            byte[] calleeIdBytes = calleeId.getBytes();
            System.arraycopy(calleeIdBytes, 0, calleeBytes, 0, Math.min(calleeIdBytes.length, 20));
            buffer.put(calleeBytes);
            
            // Other fields
            buffer.putInt(ringCount);
            buffer.putLong(ringDuration);
            buffer.putFloat(billingAmount);
            buffer.putShort(callQuality);
            
            return buffer.array();
        }
        
        public static CallData deserialize(byte[] data) {
            if (data.length < CALL_DATA_SIZE) {
                return new CallData("", "", 0, 0L, 0.0f, (short) 0);
            }
            
            ByteBuffer buffer = ByteBuffer.wrap(data);
            
            // Read caller ID
            byte[] callerBytes = new byte[20];
            buffer.get(callerBytes);
            String callerId = new String(callerBytes).trim().replaceAll("\0", "");
            
            // Read callee ID
            byte[] calleeBytes = new byte[20];
            buffer.get(calleeBytes);
            String calleeId = new String(calleeBytes).trim().replaceAll("\0", "");
            
            // Read other fields
            int ringCount = buffer.getInt();
            long ringDuration = buffer.getLong();
            float billingAmount = buffer.getFloat();
            short callQuality = buffer.getShort();
            
            return new CallData(callerId, calleeId, ringCount, ringDuration, billingAmount, callQuality);
        }
        
        // Getters
        public String getCallerId() { return callerId; }
        public String getCalleeId() { return calleeId; }
        public int getRingCount() { return ringCount; }
        public long getRingDuration() { return ringDuration; }
        public float getBillingAmount() { return billingAmount; }
        public short getCallQuality() { return callQuality; }
    }
    
    /**
     * Machine state snapshot for reading
     */
    public static class MachineStateSnapshot {
        public final String machineId;
        public final CallState state;
        public final long timestamp;
        public final CallData callData;
        
        public MachineStateSnapshot(String machineId, CallState state, long timestamp, CallData callData) {
            this.machineId = machineId;
            this.state = state;
            this.timestamp = timestamp;
            this.callData = callData;
        }
        
        @Override
        public String toString() {
            return String.format("MachineState{id=%s, state=%s, timestamp=%d, caller=%s, callee=%s}",
                machineId, state, timestamp, 
                callData != null ? callData.getCallerId() : "null",
                callData != null ? callData.getCalleeId() : "null");
        }
    }
}