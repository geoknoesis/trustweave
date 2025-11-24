#!/bin/bash
# Script to analyze Kotlin Gradle Plugin source code for the archivesTaskOutputAsFriendModule bug

set -e

echo "=== Kotlin Gradle Plugin Bug Analysis Script ==="
echo ""

# Check if repository is cloned
if [ ! -d "kotlin" ]; then
    echo "Cloning Kotlin repository..."
    git clone https://github.com/JetBrains/kotlin.git
    cd kotlin
    echo "Checking out version 2.2.21..."
    git checkout v2.2.21
else
    cd kotlin
    echo "Repository already exists, checking out v2.2.21..."
    git checkout v2.2.21
fi

echo ""
echo "=== Searching for archivesTaskOutputAsFriendModule ==="
echo ""

PLUGIN_DIR="libraries/tools/gradle-plugin"

if [ ! -d "$PLUGIN_DIR" ]; then
    echo "ERROR: Plugin directory not found at $PLUGIN_DIR"
    exit 1
fi

echo "1. Finding all references to archivesTaskOutputAsFriendModule:"
echo "------------------------------------------------------------"
git grep -n "archivesTaskOutputAsFriendModule" "$PLUGIN_DIR" || echo "No direct references found"

echo ""
echo "2. Finding property reading patterns:"
echo "------------------------------------------------------------"
git grep -n "findProperty\|gradleProperty\|getProperty" "$PLUGIN_DIR" | grep -i "archives\|friend" || echo "No property reading found"

echo ""
echo "3. Finding task dependency creation (compileKotlin -> jar):"
echo "------------------------------------------------------------"
git grep -n "dependsOn.*jar\|jar.*dependsOn" "$PLUGIN_DIR" || echo "No direct jar dependency found"
git grep -n "compileKotlin.*dependsOn\|dependsOn.*compileKotlin" "$PLUGIN_DIR" || echo "No compileKotlin dependency found"

echo ""
echo "4. Finding friend module related code:"
echo "------------------------------------------------------------"
git grep -n "friend.*module\|FriendModule" "$PLUGIN_DIR" -i || echo "No friend module code found"

echo ""
echo "5. Finding archive task related code:"
echo "------------------------------------------------------------"
git grep -n "archiveTask\|archivesTask" "$PLUGIN_DIR" -i || echo "No archive task code found"

echo ""
echo "6. Checking recent changes (commits mentioning friend or archive):"
echo "------------------------------------------------------------"
git log --oneline --all --grep="friend\|archive" -i "$PLUGIN_DIR" | head -20 || echo "No recent changes found"

echo ""
echo "=== Analysis Complete ==="
echo ""
echo "Next steps:"
echo "1. Review the files found above"
echo "2. Look for where the property is read vs where dependencies are created"
echo "3. Check if there's a timing issue (dependencies created before property is read)"
echo "4. Compare with v2.2.20 to see what changed: git diff v2.2.20..v2.2.21 -- $PLUGIN_DIR"

