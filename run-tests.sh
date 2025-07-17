#!/bin/bash

# State Machine Test Runner Script
# Usage: ./run-tests.sh [test-class-name]

set -e  # Exit on any error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}🚀 State Machine Test Runner${NC}"
echo "=================================="

# Check if MySQL is running
echo -e "${YELLOW}📋 Checking MySQL connection...${NC}"
if ! mysql -u root -p123456 -e "SELECT 1;" > /dev/null 2>&1; then
    echo -e "${RED}❌ MySQL connection failed. Please check:${NC}"
    echo "   - MySQL service is running: sudo service mysql start"
    echo "   - Credentials are correct (root/123456)"
    echo "   - Port 3306 is accessible"
    exit 1
fi
echo -e "${GREEN}✅ MySQL connection successful${NC}"

# Setup test database
echo -e "${YELLOW}🗄️ Setting up test database...${NC}"
mysql -u root -p123456 << EOF
CREATE DATABASE IF NOT EXISTS statemachine_test;
USE statemachine_test;

-- Create call_snapshots table
CREATE TABLE IF NOT EXISTS call_snapshots (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    machine_id VARCHAR(255) NOT NULL,
    state VARCHAR(100) NOT NULL,
    context_data TEXT,
    timestamp TIMESTAMP NOT NULL,
    is_offline BOOLEAN DEFAULT FALSE,
    INDEX idx_machine_id (machine_id),
    INDEX idx_timestamp (timestamp)
);

-- Create smsmachine_snapshots table
CREATE TABLE IF NOT EXISTS smsmachine_snapshots (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    machine_id VARCHAR(255) NOT NULL,
    state VARCHAR(100) NOT NULL,
    context_data TEXT,
    timestamp TIMESTAMP NOT NULL,
    is_offline BOOLEAN DEFAULT FALSE,
    INDEX idx_machine_id (machine_id),
    INDEX idx_timestamp (timestamp)
);

-- Clean any existing test data
DELETE FROM call_snapshots;
DELETE FROM smsmachine_snapshots;
EOF

echo -e "${GREEN}✅ Test database setup complete${NC}"

# Compile project
echo -e "${YELLOW}🔨 Compiling project...${NC}"
if ! mvn compile test-compile -q; then
    echo -e "${RED}❌ Compilation failed${NC}"
    exit 1
fi
echo -e "${GREEN}✅ Compilation successful${NC}"

# Run tests
echo -e "${YELLOW}🧪 Running tests...${NC}"
echo ""

# Determine which tests to run
if [ -z "$1" ]; then
    # Run all tests
    TEST_PATTERN="com.telcobright.statemachine.test.*"
    echo -e "${BLUE}Running all state machine tests...${NC}"
else
    # Run specific test class
    TEST_PATTERN="$1"
    echo -e "${BLUE}Running test: $1${NC}"
fi

# Execute tests with detailed output
mvn test -Dtest="$TEST_PATTERN" \
    -Dmaven.test.failure.ignore=true \
    -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn

TEST_EXIT_CODE=$?

echo ""

# Check test results
if [ $TEST_EXIT_CODE -eq 0 ]; then
    echo -e "${GREEN}✅ All tests passed!${NC}"
else
    echo -e "${YELLOW}⚠️ Some tests may have failed. Check output above.${NC}"
fi

# Display test summary
echo -e "${BLUE}📊 Test Summary:${NC}"
echo "=================="

# Count test results from surefire reports
if [ -d "target/surefire-reports" ]; then
    TOTAL_TESTS=$(find target/surefire-reports -name "*.xml" -exec grep -l "testcase" {} \; | wc -l)
    FAILED_TESTS=$(find target/surefire-reports -name "*.xml" -exec grep -l "failure\|error" {} \; | wc -l)
    
    echo "Test files found: $TOTAL_TESTS"
    echo "Failed test files: $FAILED_TESTS"
    
    if [ $FAILED_TESTS -gt 0 ]; then
        echo -e "${RED}❌ Failed tests found in:${NC}"
        find target/surefire-reports -name "*.xml" -exec grep -l "failure\|error" {} \;
    fi
fi

# Cleanup option
echo ""
read -p "🗑️ Clean up test database? (y/n): " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo -e "${YELLOW}🧹 Cleaning up test database...${NC}"
    mysql -u root -p123456 -e "DROP DATABASE IF EXISTS statemachine_test;"
    echo -e "${GREEN}✅ Test database cleaned up${NC}"
fi

echo ""
echo -e "${BLUE}🎯 Test run complete!${NC}"

# Exit with test result code
exit $TEST_EXIT_CODE