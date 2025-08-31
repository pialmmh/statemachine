#!/usr/bin/env python3
import mysql.connector
import sys

# Database connection parameters - adjust as needed
configs = [
    {'host': '127.0.0.1', 'user': 'root', 'password': '', 'database': 'statedb'},
    {'host': '127.0.0.1', 'user': 'root', 'password': 'root', 'database': 'statedb'},
    {'host': 'localhost', 'user': 'root', 'password': '', 'database': 'statedb'},
    {'host': 'localhost', 'user': 'root', 'password': 'root', 'database': 'statedb'},
]

conn = None
cursor = None

for config in configs:
    try:
        print(f"Trying connection with: host={config['host']}, user={config['user']}, password={'***' if config['password'] else 'NO_PASSWORD'}")
        conn = mysql.connector.connect(**config)
        cursor = conn.cursor()
        print("✓ Connected successfully!")
        break
    except mysql.connector.Error as err:
        print(f"  Failed: {err}")
        continue

if not conn:
    print("ERROR: Could not connect to database with any configuration")
    sys.exit(1)

try:
    # Get list of all tables
    cursor.execute("SHOW TABLES")
    all_tables = [table[0] for table in cursor.fetchall()]
    print(f"\nFound {len(all_tables)} tables in database")
    
    # Drop history tables
    history_tables = [t for t in all_tables if t.startswith('history_')]
    for table in history_tables:
        print(f"Dropping table: {table}")
        cursor.execute(f"DROP TABLE IF EXISTS {table}")
    
    # Truncate context and registry tables
    tables_to_truncate = [
        'state_machine_contexts',
        'call_persistent_context',
        'vm_context', 
        'chat_context',
        'registry_call',
        'registry_simple',
        'registry_vm',
        'registry_chat'
    ]
    
    for table in tables_to_truncate:
        if table in all_tables:
            print(f"Truncating table: {table}")
            cursor.execute(f"TRUNCATE TABLE {table}")
    
    # Commit changes
    conn.commit()
    
    # Show remaining tables
    cursor.execute("SHOW TABLES")
    remaining_tables = cursor.fetchall()
    print(f"\n✓ Database cleaned successfully!")
    print(f"Remaining tables: {len(remaining_tables)}")
    for table in remaining_tables:
        print(f"  - {table[0]}")
    
except mysql.connector.Error as err:
    print(f"Error during cleanup: {err}")
    if conn:
        conn.rollback()
finally:
    if cursor:
        cursor.close()
    if conn:
        conn.close()