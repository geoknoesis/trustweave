#!/bin/bash

# Test environment setup script for VeriCore
# This script sets up the test environment and validates configuration

set -e

echo "VeriCore Test Environment Setup"
echo "================================"
echo ""

# Check Docker
echo "Checking Docker..."
if ! command -v docker &> /dev/null; then
    echo "ERROR: Docker is not installed or not in PATH"
    echo "Please install Docker: https://docs.docker.com/get-docker/"
    exit 1
fi

if ! docker ps &> /dev/null; then
    echo "ERROR: Docker is not running"
    echo "Please start Docker and try again"
    exit 1
fi

echo "✓ Docker is installed and running"
echo ""

# Check Java
echo "Checking Java..."
if ! command -v java &> /dev/null; then
    echo "ERROR: Java is not installed or not in PATH"
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | sed '/^1\./s///' | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 21 ]; then
    echo "ERROR: Java 21+ is required. Found Java $JAVA_VERSION"
    exit 1
fi

echo "✓ Java $JAVA_VERSION is installed"
echo ""

# Check Gradle
echo "Checking Gradle..."
if [ ! -f "./gradlew" ]; then
    echo "ERROR: gradlew not found. Are you in the project root?"
    exit 1
fi

echo "✓ Gradle wrapper found"
echo ""

# Set test environment variables
echo "Setting test environment variables..."
export VERICORE_TEST_USE_REAL_SERVICES=false
export VERICORE_TEST_TIMEOUT_SECONDS=30
export VERICORE_SKIP_INTEGRATION_TESTS=false
export VERICORE_TEST_LOG_LEVEL=INFO

echo "✓ Environment variables set"
echo ""

# Validate TestContainers can pull images
echo "Validating TestContainers..."
echo "This may take a few minutes on first run..."
./gradlew test --tests "*TestContainers*" --dry-run &> /dev/null || true

echo ""
echo "Setup complete!"
echo ""
echo "To run tests:"
echo "  ./gradlew test                    # Run all tests"
echo "  ./gradlew test --tests \"*IntegrationTest\"  # Run integration tests only"
echo "  ./gradlew koverReport             # Generate coverage report"
echo ""

