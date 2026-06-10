#!/bin/bash

# Run tests for a specific plugin
# Usage: ./run-plugin-tests.sh <plugin-path>
# Examples:
#   ./run-plugin-tests.sh did:key
#   ./run-plugin-tests.sh kms:aws
#   ./run-plugin-tests.sh chains:ethereum

set -e

if [ -z "$1" ]; then
    echo "Usage: ./run-plugin-tests.sh <plugin-path>"
    echo ""
    echo "Examples:"
    echo "  ./run-plugin-tests.sh did:key"
    echo "  ./run-plugin-tests.sh kms:aws"
    echo "  ./run-plugin-tests.sh chains:ethereum"
    exit 1
fi

PLUGIN_PATH=$1

# Convert plugin path to Gradle project path
case "$PLUGIN_PATH" in
    did:*)
        METHOD=$(echo "$PLUGIN_PATH" | cut -d: -f2)
        PROJECT_PATH=":did:plugins:$METHOD"
        ;;
    kms:*)
        PROVIDER=$(echo "$PLUGIN_PATH" | cut -d: -f2)
        PROJECT_PATH=":kms:plugins:$PROVIDER"
        ;;
    chains:*)
        CHAIN=$(echo "$PLUGIN_PATH" | cut -d: -f2)
        PROJECT_PATH=":chains:plugins:$CHAIN"
        ;;
    core:*)
        PLUGIN=$(echo "$PLUGIN_PATH" | cut -d: -f2)
        PROJECT_PATH=":core:plugins:$PLUGIN"
        ;;
    *)
        echo "ERROR: Unknown plugin path format: $PLUGIN_PATH"
        echo "Supported formats: did:<method>, kms:<provider>, chains:<chain>, core:<plugin>"
        exit 1
        ;;
esac

echo "Running tests for plugin: $PLUGIN_PATH"
echo "Project path: $PROJECT_PATH"
echo ""

./gradlew "$PROJECT_PATH:test" "$PROJECT_PATH:koverReport"

echo ""
echo "Tests completed!"
echo "Coverage report: build/modules/$PROJECT_PATH/reports/kover/index.html"

