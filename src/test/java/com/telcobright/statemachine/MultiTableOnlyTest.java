package com.telcobright.statemachine;

import com.telcobright.core.entity.ShardingEntity;
import com.telcobright.core.annotation.*;
import java.time.LocalDateTime;

/**
 * Simple test to validate ShardingEntity implementation
 */
public class MultiTableOnlyTest {

    /**
     * Simple entity implementing ShardingEntity correctly
     */
    @Table(name = "test_entity")
    static class TestEntity implements ShardingEntity {
        @Id
        @Column(name = "id")
        private String id;

        @ShardingKey
        @Column(name = "created_at")
        private LocalDateTime createdAt;

        @Column(name = "data")
        private String data;

        public TestEntity(String id) {
            this.id = id;
            this.createdAt = LocalDateTime.now();
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public void setId(String id) {
            this.id = id;
        }

        @Override
        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        @Override
        public void setCreatedAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
        }

        public String getData() {
            return data;
        }

        public void setData(String data) {
            this.data = data;
        }
    }

    public static void main(String[] args) {
        System.out.println("Testing ShardingEntity implementation...");

        TestEntity entity = new TestEntity("TEST-001");
        entity.setData("Test data");

        System.out.println("Entity ID: " + entity.getId());
        System.out.println("Entity CreatedAt: " + entity.getCreatedAt());
        System.out.println("Entity Data: " + entity.getData());

        // Verify interface methods work
        ShardingEntity shardingEntity = entity;
        System.out.println("Via interface - ID: " + shardingEntity.getId());
        System.out.println("Via interface - CreatedAt: " + shardingEntity.getCreatedAt());

        System.out.println("✅ ShardingEntity implementation validated!");
    }
}