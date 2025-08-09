package com.telcobright.statemachine.monitoring;

/**
 * Default implementation of MachineSnapshot used by monitoring classes
 * when no specific snapshot entity type is provided.
 */
public class DefaultMachineSnapshot extends AbstractMachineSnapshot {
    
    public DefaultMachineSnapshot() {
        super();
    }
    
    @Override
    public Object getShardingKey() {
        return getMachineId();
    }
    
    @Override
    public String toString() {
        return String.format("DefaultMachineSnapshot{id=%s, machine=%s, %s->%s, v%d}", 
                            getSnapshotId(), getMachineId(), getStateBefore(), getStateAfter(), getVersion());
    }
}