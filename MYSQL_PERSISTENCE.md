# MySQL Persistence for State Machine

This document describes the MySQL-based persistence implementation for the state machine library, including support for custom SQL execution.

## Features

- **JDBC MySQL Integration** - Built on MySQL Connector/J with HikariCP connection pooling
- **Custom Save Functions** - Override default save behavior with custom SQL
- **Custom Load Functions** - Override default load behavior with complex queries
- **Custom Initialization** - Execute custom SQL during repository initialization
- **Direct SQL Execution** - Execute arbitrary SQL queries and updates
- **Transaction Support** - Execute multiple operations in a single transaction

## Quick Start

### Properties-Based Configuration (Recommended)

Create a `statemachine-db.properties` file:

```properties
statemachine.db.url=jdbc:mysql://localhost:3306/statemachine_db
statemachine.db.username=your_username
statemachine.db.password=your_password
statemachine.db.tableName=state_machine_snapshots
statemachine.db.pool.maximumPoolSize=10
```

Then use it in your code:

```java
StateMachineWrapper machine = FluentStateMachineBuilder.create("order-123")
    .withDatabasePersistenceFromProperties()
    .initialState("CREATED")
    .state("CREATED")
    .state("PROCESSING")
    .state("COMPLETED")
    .transition("CREATED", "PROCESS", "PROCESSING")
    .transition("PROCESSING", "COMPLETE", "COMPLETED")
    .buildAndStart();
```

### Basic MySQL Persistence (Programmatic)

```java
StateMachineWrapper machine = FluentStateMachineBuilder.create("order-123")
    .withDatabasePersistence(
        "jdbc:mysql://localhost:3306/statemachine_db",
        "username",
        "password"
    )
    .initialState("CREATED")
    .state("CREATED")
    .state("PROCESSING")
    .state("COMPLETED")
    .transition("CREATED", "PROCESS", "PROCESSING")
    .transition("PROCESSING", "COMPLETE", "COMPLETED")
    .buildAndStart();
```

### Custom Table Name

```java
StateMachineWrapper machine = FluentStateMachineBuilder.create("order-123")
    .withDatabasePersistence(
        "jdbc:mysql://localhost:3306/statemachine_db",
        "username",
        "password",
        "custom_snapshots_table"  // Custom table name
    )
    .initialState("CREATED")
    .buildAndStart();
```

## Custom SQL Functions

### Custom Save Function

Save additional metadata or write to multiple tables:

```java
FluentStateMachineBuilder.create("order-123")
    .withDatabasePersistence(jdbcUrl, username, password)
    .withCustomSaveFunction((connection, snapshot) -> {
        try {
            // Save with additional columns
            String sql = """
                INSERT INTO order_snapshots 
                (machine_id, state_id, context, user_id, order_total, timestamp) 
                VALUES (?, ?, ?, ?, ?, ?)
                """;
            
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, snapshot.getMachineId());
                pstmt.setString(2, snapshot.getStateId());
                pstmt.setString(3, snapshot.getContext());
                
                // Extract custom data from context
                JSONObject ctx = new JSONObject(snapshot.getContext());
                pstmt.setString(4, ctx.getString("userId"));
                pstmt.setDouble(5, ctx.getDouble("orderTotal"));
                pstmt.setTimestamp(6, Timestamp.valueOf(snapshot.getTimestamp()));
                
                pstmt.executeUpdate();
            }
            
            // Also write to audit log
            auditLog(connection, snapshot);
            
            return true; // Indicate custom save was successful
            
        } catch (SQLException e) {
            return false; // Fall back to default save
        }
    })
    .buildAndStart();
```

### Custom Load Function

Load with joins or complex queries:

```java
FluentStateMachineBuilder.create("order-123")
    .withDatabasePersistence(jdbcUrl, username, password)
    .withCustomLoadFunction((connection, machineId) -> {
        String sql = """
            SELECT s.*, u.name, u.email, o.total
            FROM state_snapshots s
            JOIN users u ON s.user_id = u.id
            JOIN orders o ON s.machine_id = o.id
            WHERE s.machine_id = ?
            ORDER BY s.timestamp DESC
            LIMIT 1
            """;
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, machineId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    // Create enriched snapshot
                    StateMachineSnapshotEntity snapshot = new StateMachineSnapshotEntity(
                        rs.getString("machine_id"),
                        rs.getString("state_id"),
                        enrichContext(rs),  // Add user and order data to context
                        rs.getBoolean("is_offline")
                    );
                    return snapshot;
                }
            }
        } catch (SQLException e) {
            return null; // Fall back to default load
        }
    })
    .buildAndStart();
```

### Custom Initialization

Create custom tables or indexes:

```java
FluentStateMachineBuilder.create("order-123")
    .withDatabasePersistence(jdbcUrl, username, password)
    .withCustomInitFunction(connection -> {
        try {
            // Create custom table
            connection.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS order_state_snapshots (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    machine_id VARCHAR(255) NOT NULL,
                    state_id VARCHAR(255) NOT NULL,
                    order_id VARCHAR(100),
                    user_id VARCHAR(100),
                    total DECIMAL(10,2),
                    context JSON,
                    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    INDEX idx_order (order_id),
                    INDEX idx_user (user_id)
                )
                """);
            
            // Create audit table
            connection.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS state_audit_log (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    machine_id VARCHAR(255),
                    transition VARCHAR(500),
                    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
            
            return true;
        } catch (SQLException e) {
            return false;
        }
    })
    .buildAndStart();
```

## Direct SQL Execution

For advanced use cases, you can execute SQL directly:

```java
// Get the repository
DatabaseConnectionManager connManager = new DatabaseConnectionManager(jdbcUrl, user, pass);
DatabaseStateMachineSnapshotRepository repo = new DatabaseStateMachineSnapshotRepository(connManager);

// Execute a query
List<StateStats> stats = repo.executeCustomQuery(
    "SELECT state_id, COUNT(*) as count FROM snapshots GROUP BY state_id",
    rs -> {
        List<StateStats> results = new ArrayList<>();
        while (rs.next()) {
            results.add(new StateStats(
                rs.getString("state_id"),
                rs.getInt("count")
            ));
        }
        return results;
    }
);

// Execute an update
int updated = repo.executeCustomUpdate(
    "UPDATE snapshots SET context = ? WHERE machine_id = ? AND state_id = ?",
    newContext, machineId, stateId
);

// Execute in transaction
repo.executeInTransaction(connection -> {
    // Multiple operations in one transaction
    updateSnapshot(connection, snapshot1);
    updateSnapshot(connection, snapshot2);
    insertAuditLog(connection, "Bulk update");
    return true;
});
```

## Database Schema

Default table structure:

```sql
CREATE TABLE state_machine_snapshots (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    machine_id VARCHAR(255) NOT NULL,
    state_id VARCHAR(255) NOT NULL,
    context TEXT,
    is_offline BOOLEAN DEFAULT FALSE,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_machine_id (machine_id),
    INDEX idx_timestamp (timestamp),
    INDEX idx_offline (is_offline)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

## Properties-Based Configuration

### Configuration File Locations

The library looks for configuration files in the following order (highest to lowest priority):

1. **System Properties** (`-Dstatemachine.db.url=...`)
2. **Environment Variables** (`STATEMACHINE_DB_URL=...`)
3. **Configuration Files:**
   - `./statemachine-db.properties`
   - `./config/statemachine-db.properties`
   - `~/.statemachine/db.properties`
   - `/etc/statemachine/db.properties`
4. **Classpath** (`src/main/resources/statemachine-db.properties`)

### Complete Properties Reference

```properties
# ==============================================
# REQUIRED: Database Connection
# ==============================================
statemachine.db.url=jdbc:mysql://localhost:3306/statemachine_db
statemachine.db.username=your_username
statemachine.db.password=your_password

# ==============================================
# OPTIONAL: Basic Configuration
# ==============================================
statemachine.db.driver=com.mysql.cj.jdbc.Driver
statemachine.db.tableName=state_machine_snapshots

# ==============================================
# OPTIONAL: Connection Pool (HikariCP)
# ==============================================
statemachine.db.pool.minimumIdle=2
statemachine.db.pool.maximumPoolSize=10
statemachine.db.pool.connectionTimeout=30000
statemachine.db.pool.idleTimeout=600000
statemachine.db.pool.maxLifetime=1800000
statemachine.db.pool.autoCommit=true
statemachine.db.pool.validationTimeout=5000
statemachine.db.pool.leakDetectionThreshold=0

# ==============================================
# OPTIONAL: MySQL Optimizations
# ==============================================
statemachine.db.mysql.cachePrepStmts=true
statemachine.db.mysql.prepStmtCacheSize=250
statemachine.db.mysql.prepStmtCacheSqlLimit=2048
statemachine.db.mysql.useServerPrepStmts=true
statemachine.db.mysql.useLocalSessionState=true
statemachine.db.mysql.rewriteBatchedStatements=true
statemachine.db.mysql.cacheResultSetMetadata=true
statemachine.db.mysql.cacheServerConfiguration=true
statemachine.db.mysql.elideSetAutoCommits=true
statemachine.db.mysql.maintainTimeStats=false

# ==============================================
# OPTIONAL: Custom DataSource Properties
# ==============================================
statemachine.db.custom.useSSL=false
statemachine.db.custom.serverTimezone=UTC
statemachine.db.custom.characterEncoding=utf8
```

### Usage Examples

#### Load from Default Locations

```java
StateMachineWrapper machine = FluentStateMachineBuilder.create("order-123")
    .withDatabasePersistenceFromProperties()
    .buildAndStart();
```

#### Load from Specific File

```java
StateMachineWrapper machine = FluentStateMachineBuilder.create("order-123")
    .withDatabasePersistenceFromProperties("./my-config.properties")
    .buildAndStart();
```

#### Load from Properties Object

```java
Properties props = new Properties();
props.setProperty("statemachine.db.url", "jdbc:mysql://localhost:3306/mydb");
props.setProperty("statemachine.db.username", "user");
props.setProperty("statemachine.db.password", "pass");

StateMachineWrapper machine = FluentStateMachineBuilder.create("order-123")
    .withDatabasePersistenceFromProperties(props)
    .buildAndStart();
```

#### System Properties Override

```bash
java -Dstatemachine.db.url=jdbc:mysql://prod:3306/db \
     -Dstatemachine.db.username=prod_user \
     -Dstatemachine.db.password=prod_pass \
     MyApplication
```

#### Environment Variables

```bash
export STATEMACHINE_DB_URL="jdbc:mysql://localhost:3306/statemachine_db"
export STATEMACHINE_DB_USERNAME="myuser"
export STATEMACHINE_DB_PASSWORD="mypassword"
export STATEMACHINE_DB_POOL_MAXIMUM_POOL_SIZE="20"
```

## Connection Pool Configuration

The database connection pool (HikariCP) is configured with sensible defaults:

- Minimum idle connections: 2
- Maximum pool size: 10
- Connection timeout: 30 seconds
- Idle timeout: 10 minutes
- Max lifetime: 30 minutes

You can customize these through properties or programmatically:

```java
Properties props = new Properties();
props.setProperty("hikari.maximumPoolSize", "20");
props.setProperty("hikari.connectionTimeout", "60000");

DatabaseConnectionManager connManager = new DatabaseConnectionManager(
    jdbcUrl, username, password, props
);
```

## Best Practices

1. **Use Custom Functions Wisely** - Only override default behavior when necessary
2. **Handle Failures Gracefully** - Return false from custom functions to fall back to defaults
3. **Optimize Queries** - Use appropriate indexes for your access patterns
4. **Monitor Pool Usage** - Use `connectionManager.getPoolStats()` to monitor connections
5. **Clean Up Resources** - Always call `repository.shutdown()` when done

## Error Handling

All database operations throw `RuntimeException` wrapping the underlying `SQLException`. Handle appropriately:

```java
try {
    machine.fire("PROCESS");
} catch (RuntimeException e) {
    if (e.getCause() instanceof SQLException) {
        // Handle database error
        logger.error("Database error during state transition", e);
    }
}
```

## Migration from Other Persistence Types

To migrate from file-based or in-memory persistence:

1. Export existing snapshots
2. Create MySQL tables
3. Import snapshots using custom SQL
4. Update configuration to use MySQL persistence

The library maintains backward compatibility, so existing code continues to work with the new persistence layer.