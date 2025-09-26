#!/bin/bash

# Fix all entity files to use the correct ShardingEntity interface

# Fix imports - remove statewalk.annotation imports and use non-generic ShardingEntity
for file in src/test/java/com/telcobright/statewalk/test_multientity/entities/*.java; do
    echo "Fixing $file"

    # Remove statewalk.annotation import
    sed -i '/import com.telcobright.statewalk.annotation/d' "$file"

    # Fix ShardingEntity to be non-generic
    sed -i 's/implements ShardingEntity<LocalDateTime>/implements ShardingEntity/g' "$file"
    sed -i 's/implements ShardingEntity<String>/implements ShardingEntity/g' "$file"

    # Replace getPartitionColValue with getCreatedAt
    sed -i 's/getPartitionColValue()/getCreatedAt()/g' "$file"
    sed -i 's/setPartitionColValue(/setCreatedAt(/g' "$file"

    # Remove duplicate getCreatedAt/setCreatedAt method declarations
    sed -i '/\/\/ Direct getters\/setters for createdAt/,/^    }/d' "$file"

    # Fix the @Override issue for interface methods
    sed -i 's/@Override.*LocalDateTime getCreatedAt/@Override\n    public LocalDateTime getCreatedAt/g' "$file"
    sed -i 's/@Override.*void setCreatedAt/@Override\n    public void setCreatedAt/g' "$file"
done

echo "All entity files fixed!"