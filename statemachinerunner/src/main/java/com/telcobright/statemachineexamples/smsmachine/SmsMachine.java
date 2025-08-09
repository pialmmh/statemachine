package com.telcobright.statemachineexamples.smsmachine;

import com.telcobright.statemachine.FluentStateMachineBuilder;
import com.telcobright.statemachine.GenericStateMachine;
import com.telcobright.statemachine.events.StateMachineEvent;
import com.telcobright.statemachine.persistence.IdLookUpMode;
import com.telcobright.statemachineexamples.smsmachine.entity.SmsEntity;
import com.telcobright.statemachineexamples.smsmachine.events.SendAttempt;
import com.telcobright.statemachineexamples.smsmachine.events.DeliveryReport;
import com.telcobright.statemachineexamples.smsmachine.events.SendFailed;
import com.telcobright.statemachineexamples.smsmachine.events.StatusUpdate;
import com.telcobright.statemachineexamples.smsmachine.events.Retry;
import com.telcobright.statemachineexamples.smsmachine.states.sending.OnStatusUpdate_SENDING;
import com.telcobright.db.PartitionedRepository;

/**
 * SmsMachine example using ShardingEntity-based persistence with ByIdAndDateRange lookup mode
 * 
 * Features demonstrated:
 * - GenericStateMachine<SmsEntity, SmsContext> with ShardingEntity persistence
 * - SmsEntity (persisted) vs SmsContext (volatile)
 * - PartitionedRepository persistence with ByIdAndDateRange lookup for high volume
 * - Date-based partitioning for efficient SMS state management
 * - Long ID format with embedded timestamp for partition pruning
 * 
 * Requirements:
 * - machineId must be a long value with embedded timestamp
 * - Use com.telcobright.idkit.IdGenerator for ID generation and timestamp extraction
 */
public class SmsMachine {
    
    /**
     * Create SmsMachine with PartitionedRepository persistence using ByIdAndDateRange lookup
     * 
     * This mode is optimized for high-volume SMS processing where:
     * - Machine IDs are long values with embedded timestamps
     * - Date-based partitioning enables efficient lookups
     * - Partition pruning reduces query time for large datasets
     * 
     * @param machineId Long identifier with embedded timestamp (must be parseable as long)
     * @param partitionedRepo Repository for state persistence
     * @return Configured SmsMachine ready for high-volume use
     */
    public static GenericStateMachine<SmsEntity, SmsContext> create(String machineId, PartitionedRepository<SmsEntity, String> partitionedRepo) {
        // Validate that machineId is a valid long for ByIdAndDateRange mode
        try {
            Long.parseLong(machineId);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("SmsMachine with ByIdAndDateRange mode requires machineId to be a valid long: " + machineId, e);
        }
        
        return FluentStateMachineBuilder.<SmsEntity, SmsContext>create(machineId)
            // Configure PartitionedRepository with ByIdAndDateRange lookup mode for high volume
            .withShardingRepo(partitionedRepo, IdLookUpMode.ByIdAndDateRange, SmsEntity.class)
            
            // Set initial state
            .initialState(SmsMachineState.QUEUED)
            
            // QUEUED state - SMS waiting to be sent
            .state(SmsMachineState.QUEUED)
                .offline()  // Offline state for persistence
                .on(SendAttempt.class).to(SmsMachineState.SENDING)
                .done()
                
            // SENDING state - SMS being transmitted with status updates
            .state(SmsMachineState.SENDING)
                .on(DeliveryReport.class).to(SmsMachineState.DELIVERED)
                .on(SendFailed.class).to(SmsMachineState.FAILED)
                .stay(StatusUpdate.class, (machine, event) -> 
                    OnStatusUpdate_SENDING.handle(machine, event))
                .done()
                
            // DELIVERED state - SMS successfully delivered
            .state(SmsMachineState.DELIVERED)
                .done()
                
            // FAILED state - SMS delivery failed, can retry
            .state(SmsMachineState.FAILED)
                .on(Retry.class).to(SmsMachineState.QUEUED)
                .done()
                
            .build();
    }
    
    /**
     * Create SmsMachine with default in-memory persistence (for testing)
     * Note: Uses ById mode since we don't need date-based partitioning for testing
     */
    public static GenericStateMachine<SmsEntity, SmsContext> create(String machineId) {
        return FluentStateMachineBuilder.<SmsEntity, SmsContext>create(machineId)
            .initialState(SmsMachineState.QUEUED)
            
            .state(SmsMachineState.QUEUED)
                .offline()
                .on(SendAttempt.class).to(SmsMachineState.SENDING)
                .done()
                
            .state(SmsMachineState.SENDING)
                .on(DeliveryReport.class).to(SmsMachineState.DELIVERED)
                .on(SendFailed.class).to(SmsMachineState.FAILED)
                .stay(StatusUpdate.class, (machine, event) -> 
                    OnStatusUpdate_SENDING.handle(machine, event))
                .done()
                
            .state(SmsMachineState.DELIVERED)
                .done()
                
            .state(SmsMachineState.FAILED)
                .on(Retry.class).to(SmsMachineState.QUEUED)
                .done()
                
            .build();
    }
    
    /**
     * Create SmsMachine with context and long ID for high-volume processing
     * 
     * @param machineId Long identifier with embedded timestamp from IdGenerator
     * @param context SMS context with delivery details
     * @param partitionedRepo Repository for date-partitioned storage
     * @return Configured SmsMachine with context ready for processing
     */
    public static GenericStateMachine<SmsEntity, SmsContext> createWithContext(String machineId, SmsContext context, 
                                             PartitionedRepository<SmsEntity, String> partitionedRepo) {
        GenericStateMachine<SmsEntity, SmsContext> machine = create(machineId, partitionedRepo);
        machine.setContext(context);
        return machine;
    }
    
    /**
     * Create SmsMachine using IdGenerator for timestamp-embedded ID
     * Demonstrates proper ID generation for ByIdAndDateRange mode
     * 
     * @param partitionedRepo Repository for persistence
     * @return SmsMachine with properly generated long ID
     */
    public static GenericStateMachine<SmsEntity, SmsContext> createWithGeneratedId(PartitionedRepository<SmsEntity, String> partitionedRepo) {
        // Generate ID with embedded timestamp using IdGenerator
        long generatedId = com.telcobright.idkit.IdGenerator.generateId();
        return create(String.valueOf(generatedId), partitionedRepo);
    }
}