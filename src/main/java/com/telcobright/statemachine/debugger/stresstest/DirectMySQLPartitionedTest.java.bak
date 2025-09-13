package com.telcobright.statemachine.debugger.stresstest;

import com.telcobright.statemachine.db.MySQLPartitionedRepository;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Direct test of MySQL partitioned repository without registry
 */
public class DirectMySQLPartitionedTest {
    
    public static void main(String[] args) throws Exception {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("   DIRECT MySQL PARTITIONED REPOSITORY TEST");
        System.out.println("=".repeat(80));
        System.out.println();
        
        runTest();
    }
    
    private static DataSource createDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://127.0.0.1:3306/statemachine_test?createDatabaseIfNotExist=true&useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true");
        config.setUsername("root");
        config.setPassword("123456");
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setMaximumPoolSize(5);
        config.setConnectionTimeout(30000);
        return new HikariDataSource(config);
    }
    
    private static void runTest() throws Exception {
        DataSource dataSource = null;
        
        try {
            // Create database connection
            System.out.println("📋 Connecting to MySQL database...");
            dataSource = createDataSource();
            System.out.println("✅ Connected to MySQL at 127.0.0.1:3306");
            
            // Test HASH partitioning
            testHashPartitioning(dataSource);
            
            // Test MONTHLY partitioning
            testMonthlyPartitioning(dataSource);
            
        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (dataSource instanceof HikariDataSource) {
                ((HikariDataSource) dataSource).close();
            }
        }
    }
    
    private static void testHashPartitioning(DataSource dataSource) {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("   TEST 1: HASH PARTITIONING");
        System.out.println("=".repeat(60));
        
        try {
            // Create repository with hash partitioning
            MySQLPartitionedRepository<SimpleCallEntity, String> repository = 
                new MySQLPartitionedRepository<>(
                    dataSource, 
                    SimpleCallEntity.class, 
                    "test_hash_partitions",
                    MySQLPartitionedRepository.PartitionStrategy.HASH
                );
            repository.setHashPartitionCount(4); // 4 partitions
            
            System.out.println("\n📋 Inserting test entities...");
            
            // Insert test entities
            for (int i = 0; i < 20; i++) {
                SimpleCallEntity entity = new SimpleCallEntity("hash-" + i);
                entity.setCallerId("+1-555-" + String.format("%04d", i));
                entity.setCalleeId("+1-555-" + String.format("%04d", i + 1));
                entity.setCurrentState("IDLE");
                entity.setLastStateChange(LocalDateTime.now());
                
                repository.insert(entity);
            }
            System.out.println("✅ Inserted 20 entities");
            
            // Test retrieval
            System.out.println("\n📋 Testing retrieval...");
            int found = 0;
            for (int i = 0; i < 20; i++) {
                SimpleCallEntity entity = repository.findById("hash-" + i);
                if (entity != null) {
                    found++;
                    if (i < 3) { // Show first 3
                        System.out.println("  Found: hash-" + i + 
                            " (state: " + entity.getCurrentState() + 
                            ", partition: " + entity.getShardingKey() + ")");
                    }
                }
            }
            System.out.println("✅ Retrieved " + found + "/20 entities");
            
            // Update some entities
            System.out.println("\n📋 Testing updates...");
            SimpleCallEntity entity = repository.findById("hash-0");
            if (entity != null) {
                entity.setCurrentState("RINGING");
                repository.updateById("hash-0", entity);
                
                // Retrieve again to verify
                entity = repository.findById("hash-0");
                System.out.println("✅ Updated hash-0 state to: " + entity.getCurrentState());
            }
            
            repository.printStatistics();
            
        } catch (Exception e) {
            System.err.println("❌ Hash partitioning test failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void testMonthlyPartitioning(DataSource dataSource) {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("   TEST 2: MONTHLY PARTITIONING");
        System.out.println("=".repeat(60));
        
        try {
            // Create repository with monthly partitioning
            MySQLPartitionedRepository<SimpleCallEntity, String> repository = 
                new MySQLPartitionedRepository<>(
                    dataSource, 
                    SimpleCallEntity.class, 
                    "test_monthly",
                    MySQLPartitionedRepository.PartitionStrategy.MONTHLY
                );
            repository.setAutoCreatePartitions(true);
            
            String currentMonth = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
            System.out.println("\n📋 Current month for partitioning: " + currentMonth);
            System.out.println("📋 Inserting test entities...");
            
            // Insert test entities
            for (int i = 0; i < 10; i++) {
                SimpleCallEntity entity = new SimpleCallEntity("monthly-" + i);
                entity.setCallerId("+1-555-" + String.format("%04d", i));
                entity.setCalleeId("+1-555-" + String.format("%04d", i + 1));
                entity.setCurrentState("IDLE");
                entity.setLastStateChange(LocalDateTime.now());
                
                repository.insert(entity);
            }
            System.out.println("✅ Inserted 10 entities into monthly partition");
            
            // Test retrieval
            System.out.println("\n📋 Testing retrieval from monthly partition...");
            int found = 0;
            for (int i = 0; i < 10; i++) {
                SimpleCallEntity entity = repository.findById("monthly-" + i);
                if (entity != null) {
                    found++;
                    if (i < 3) { // Show first 3
                        System.out.println("  Found: monthly-" + i + 
                            " (state: " + entity.getCurrentState() + ")");
                    }
                }
            }
            System.out.println("✅ Retrieved " + found + "/10 entities");
            
            repository.printStatistics();
            
            System.out.println("\n✅ All MySQL Partitioned Repository Tests Complete!");
            
        } catch (Exception e) {
            System.err.println("❌ Monthly partitioning test failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}