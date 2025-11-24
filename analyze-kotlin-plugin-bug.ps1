# PowerShell script to analyze Kotlin Gradle Plugin source code for the archivesTaskOutputAsFriendModule bug

Write-Host "=== Kotlin Gradle Plugin Bug Analysis Script ===" -ForegroundColor Cyan
Write-Host ""

# Check if repository is cloned
if (-not (Test-Path "kotlin")) {
    Write-Host "Cloning Kotlin repository..." -ForegroundColor Yellow
    git clone https://github.com/JetBrains/kotlin.git
    Set-Location kotlin
    Write-Host "Checking out version 2.2.21..." -ForegroundColor Yellow
    git checkout v2.2.21
} else {
    Set-Location kotlin
    Write-Host "Repository already exists, checking out v2.2.21..." -ForegroundColor Yellow
    git checkout v2.2.21
}

Write-Host ""
Write-Host "=== Searching for archivesTaskOutputAsFriendModule ===" -ForegroundColor Cyan
Write-Host ""

$pluginDir = "libraries/tools/gradle-plugin"

if (-not (Test-Path $pluginDir)) {
    Write-Host "ERROR: Plugin directory not found at $pluginDir" -ForegroundColor Red
    exit 1
}

Write-Host "1. Finding all references to archivesTaskOutputAsFriendModule:" -ForegroundColor Green
Write-Host "------------------------------------------------------------"
git grep -n "archivesTaskOutputAsFriendModule" $pluginDir
if ($LASTEXITCODE -ne 0) {
    Write-Host "No direct references found" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "2. Finding property reading patterns:" -ForegroundColor Green
Write-Host "------------------------------------------------------------"
git grep -n "findProperty|gradleProperty|getProperty" $pluginDir | Select-String -Pattern "archives|friend" -CaseSensitive:$false
if ($LASTEXITCODE -ne 0) {
    Write-Host "No property reading found" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "3. Finding task dependency creation (compileKotlin -> jar):" -ForegroundColor Green
Write-Host "------------------------------------------------------------"
git grep -n "dependsOn.*jar|jar.*dependsOn" $pluginDir
if ($LASTEXITCODE -ne 0) {
    Write-Host "No direct jar dependency found" -ForegroundColor Yellow
}
git grep -n "compileKotlin.*dependsOn|dependsOn.*compileKotlin" $pluginDir
if ($LASTEXITCODE -ne 0) {
    Write-Host "No compileKotlin dependency found" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "4. Finding friend module related code:" -ForegroundColor Green
Write-Host "------------------------------------------------------------"
git grep -n "friend.*module|FriendModule" $pluginDir -i
if ($LASTEXITCODE -ne 0) {
    Write-Host "No friend module code found" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "5. Finding archive task related code:" -ForegroundColor Green
Write-Host "------------------------------------------------------------"
git grep -n "archiveTask|archivesTask" $pluginDir -i
if ($LASTEXITCODE -ne 0) {
    Write-Host "No archive task code found" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "6. Checking recent changes (commits mentioning friend or archive):" -ForegroundColor Green
Write-Host "------------------------------------------------------------"
git log --oneline --all --grep="friend|archive" -i $pluginDir | Select-Object -First 20
if ($LASTEXITCODE -ne 0) {
    Write-Host "No recent changes found" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "=== Analysis Complete ===" -ForegroundColor Cyan
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Yellow
Write-Host "1. Review the files found above"
Write-Host "2. Look for where the property is read vs where dependencies are created"
Write-Host "3. Check if there's a timing issue (dependencies created before property is read)"
Write-Host "4. Compare with v2.2.20: git diff v2.2.20..v2.2.21 -- $pluginDir"

