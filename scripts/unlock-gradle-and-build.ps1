# Clears Gradle daemon locks and optional stale .gradle lock files, then runs build.
# If you still see "Unable to delete file ... .jar", close Cursor/IDE (they often lock
# build outputs), then run this script from an external PowerShell, or reboot.

$ErrorActionPreference = "Continue"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

Write-Host "Stopping Gradle daemons..."
& "$root\gradlew.bat" --stop 2>$null

Write-Host "Removing .gradle lock files..."
Get-ChildItem -Path "$root\.gradle" -Recurse -Filter "*.lock" -ErrorAction SilentlyContinue |
    Remove-Item -Force -ErrorAction SilentlyContinue

# Optional: remove entire build dir (close IDE first or this may fail)
if ($args -contains "-cleanBuild") {
    Write-Host "Removing build directory (requires no locks on JARs)..."
    if (Test-Path "$root\build") {
        Remove-Item -Recurse -Force "$root\build" -ErrorAction SilentlyContinue
    }
}

Write-Host "Running gradlew build..."
& "$root\gradlew.bat" build --no-daemon @args
