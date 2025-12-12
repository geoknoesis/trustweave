# Script to clean build directory after closing IDE
# Usage: Close Cursor IDE, then run: .\clean-build.ps1

Write-Host "Cleaning build directory..." -ForegroundColor Yellow

$buildPath = "C:\Users\steph\work\trustweave\build"

if (Test-Path $buildPath) {
    try {
        # Try to remove the entire build directory
        Remove-Item -Path $buildPath -Recurse -Force -ErrorAction Stop
        Write-Host "✓ Successfully cleaned build directory" -ForegroundColor Green
    } catch {
        Write-Host "✗ Failed to clean: $($_.Exception.Message)" -ForegroundColor Red
        Write-Host "`nFiles may still be locked. Try:" -ForegroundColor Yellow
        Write-Host "1. Close Cursor IDE completely" -ForegroundColor White
        Write-Host "2. Close any Java processes: Get-Process java | Stop-Process -Force" -ForegroundColor White
        Write-Host "3. Run this script again" -ForegroundColor White
        exit 1
    }
} else {
    Write-Host "Build directory does not exist" -ForegroundColor Green
}

Write-Host "`nRunning Gradle clean..." -ForegroundColor Yellow
& .\gradlew clean --no-daemon
if ($LASTEXITCODE -eq 0) {
    Write-Host "✓ Build cleaned successfully" -ForegroundColor Green
} else {
    Write-Host "✗ Gradle clean had issues (some files may still be locked)" -ForegroundColor Yellow
}

