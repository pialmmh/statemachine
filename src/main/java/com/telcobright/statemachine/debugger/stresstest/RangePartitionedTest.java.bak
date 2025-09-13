package com.telcobright.statemachine.debugger.stresstest;

import com.telcobright.statemachine.db.MySQLPartitionedRepository;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Test native MySQL RANGE partitioning for time-based data
 */
public class RangePartitionedTest {
    
    public static void main(String[] args) throws Exception {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("   NATIVE MySQL RANGE PARTITIONING TEST");
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
            
            // Test RANGE partitioning (native MySQL time-based partitioning)
            testRangePartitioning(dataSource);
            
        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (dataSource instanceof HikariDataSource) {
                ((HikariDataSource) dataSource).close();
            }
        }
    }
    
    private static void testRangePartitioning(DataSource dataSource) {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("   TEST: NATIVE RANGE PARTITIONING");
        System.out.println("=".repeat(60));
        
        try {
            // Create repository with RANGE partitioning
            MySQLPartitionedRepository<SimpleCallEntity, String> repository = 
                new MySQLPartitionedRepository<>(
                    dataSource, 
                    SimpleCallEntity.class, 
                    "test_range_partitions",
                    MySQLPartitionedRepository.PartitionStrategy.RANGE
                );
            
            System.out.println("\n📋 Inserting test entities with timestamps...");
            
            LocalDateTime now = LocalDateTime.now();
            List<SimpleCallEntity> entities = new ArrayList<>();
            
            // Insert entities for current month
            for (int i = 0; i < 10; i++) {
                SimpleCallEntity entity = new SimpleCallEntity("range-current-" + i);
                entity.setCallerId("+1-555-" + String.format("%04d", i));
                entity.setCalleeId("+1-555-" + String.format("%04d", i + 1));
                entity.setCurrentState("IDLE");
                entity.setLastStateChange(now);
                entities.add(entity);
                repository.insert(entity);
            }
            
            // Insert entities for last month (simulated)
            for (int i = 0; i < 10; i++) {
                SimpleCallEntity entity = new SimpleCallEntity("range-lastmonth-" + i);
                entity.setCallerId("+1-555-" + String.format("%04d", i + 100));
                entity.setCalleeId("+1-555-" + String.format("%04d", i + 101));
                entity.setCurrentState("CONNECTED");
                entity.setLastStateChange(now.minusMonths(1));
                entities.add(entity);
                repository.insert(entity);
            }
            
            System.out.println("✅ Inserted 20 entities across time ranges");
            
            // Test retrieval
            System.out.println("\n📋 Testing retrieval from RANGE partitions...");
            int found = 0;
            for (int i = 0; i < 10; i++) {
                SimpleCallEntity entity = repository.findById("range-current-" + i);
                if (entity != null) {
                    found++;
                    if (i < 3) { // Show first 3
                        System.out.println("  Found: range-current-" + i + 
                            " (state: " + entity.getCurrentState() + ")");
                    }
                }
            }
            System.out.println("✅ Retrieved " + found + "/10 current month entities");
            
            // Test date range queries
            System.out.println("\n📋 Testing date range queries...");
            LocalDateTime startDate = now.minusMonths(2);
            LocalDateTime endDate = now.plusDays(1);
            
            List<SimpleCallEntity> rangeResults = repository.findAllInDateRange(startDate, endDate);
            System.out.println("✅ Found " + rangeResults.size() + " entities in date range");
            
            // Test specific month query
            LocalDateTime lastMonthStart = now.minusMonths(1).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
            LocalDateTime lastMonthEnd = lastMonthStart.plusMonths(1).minusSeconds(1);
            
            List<SimpleCallEntity> lastMonthResults = repository.findAllInDateRange(lastMonthStart, lastMonthEnd);
            System.out.println("✅ Found " + lastMonthResults.size() + " entities from last month");
            
            // Update some entities
            System.out.println("\n📋 Testing updates...");
            SimpleCallEntity entity = repository.findById("range-current-0");
            if (entity != null) {
                entity.setCurrentState("RINGING");
                repository.updateById("range-current-0", entity);
                
                // Retrieve again to verify
                entity = repository.findById("range-current-0");
                System.out.println("✅ Updated range-current-0 state to: " + entity.getCurrentState());
            }
            
            // Show partition information
            System.out.println("\n📋 Partition Information:");
            System.out.println("  Partition Type: RANGE (YEAR(created_at)*100 + MONTH(created_at))");
            System.out.println("  Current Month: " + now.format(DateTimeFormatter.ofPattern("yyyy-MM")));
            System.out.println("  Partitions created for next 12 months");
            System.out.println("  MySQL automatically routes queries to relevant partitions");
            
            repository.printStatistics();
            
            System.out.println("\n✅ Native RANGE Partitioning Test Complete!");
            
        } catch (Exception e) {
            System.err.println("❌ RANGE partitioning test failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}