# File Lock Cleanup Script for VeriCore
# This script helps identify and release file locks on Windows

Write-Host "=== VeriCore File Lock Cleanup ===" -ForegroundColor Cyan
Write-Host ""

# 1. Stop Gradle daemons
Write-Host "[1/5] Stopping Gradle daemons..." -ForegroundColor Yellow
./gradlew --stop 2>&1 | Out-Null
Start-Sleep -Seconds 2

# 2. Check for Java processes that might be holding locks
Write-Host "[2/5] Checking Java processes..." -ForegroundColor Yellow
$javaProcesses = Get-Process -Name "java" -ErrorAction SilentlyContinue
if ($javaProcesses) {
    Write-Host "   Found $($javaProcesses.Count) Java process(es)" -ForegroundColor Yellow
    Write-Host "   Note: Some may be IDE processes - be careful before killing" -ForegroundColor Yellow
} else {
    Write-Host "   No Java processes found" -ForegroundColor Green
}

# 3. Try to unlock and delete the locked file
Write-Host "[3/5] Attempting to unlock and delete locked files..." -ForegroundColor Yellow
$lockedFiles = @(
    "C:\Users\steph\work\vericore\vericore-anchor\build\libs\vericore-anchor-1.0.0-SNAPSHOT.jar",
    "C:\Users\steph\work\vericore\vericore-anchor\build\libs"
)

foreach ($file in $lockedFiles) {
    if (Test-Path $file) {
        Write-Host "   Attempting to remove: $file" -ForegroundColor Yellow
        try {
            # Try to remove read-only attribute
            if (Test-Path $file -PathType Leaf) {
                $item = Get-Item $file -Force
                $item.IsReadOnly = $false
            }
            
            # Try to delete
            Remove-Item -Path $file -Recurse -Force -ErrorAction Stop
            Write-Host "   ✓ Successfully removed: $file" -ForegroundColor Green
        } catch {
            Write-Host "   ✗ Failed to remove: $file" -ForegroundColor Red
            Write-Host "     Error: $($_.Exception.Message)" -ForegroundColor Red
        }
    }
}

# 4. Try to delete the entire build directory
Write-Host "[4/5] Attempting to clean build directory..." -ForegroundColor Yellow
$buildDir = "C:\Users\steph\work\vericore\vericore-anchor\build"
if (Test-Path $buildDir) {
    try {
        # Use robocopy trick to delete locked directories
        $emptyDir = Join-Path $env:TEMP "empty_$(Get-Random)"
        New-Item -ItemType Directory -Path $emptyDir -Force | Out-Null
        robocopy $emptyDir $buildDir /MIR /R:0 /W:0 /NFL /NDL /NJH /NJS | Out-Null
        Remove-Item $emptyDir -Force -ErrorAction SilentlyContinue
        Remove-Item $buildDir -Recurse -Force -ErrorAction Stop
        Write-Host "   ✓ Successfully cleaned build directory" -ForegroundColor Green
    } catch {
        Write-Host "   ✗ Build directory still locked" -ForegroundColor Red
        Write-Host "     You may need to close Cursor IDE and try again" -ForegroundColor Yellow
    }
}

# 5. Summary and recommendations
Write-Host ""
Write-Host "[5/5] Summary and Recommendations:" -ForegroundColor Cyan
Write-Host ""
Write-Host "Common causes of file locks on Windows:" -ForegroundColor White
Write-Host "  1. IDE (Cursor/IntelliJ) has files open or indexed" -ForegroundColor Gray
Write-Host "  2. Gradle daemon has file handles open" -ForegroundColor Gray
Write-Host "  3. Antivirus is scanning the file" -ForegroundColor Gray
Write-Host "  4. Windows Explorer has the folder open" -ForegroundColor Gray
Write-Host "  5. File system indexing service" -ForegroundColor Gray
Write-Host ""
Write-Host "If files are still locked:" -ForegroundColor Yellow
Write-Host "  1. Close Cursor IDE completely" -ForegroundColor White
Write-Host "  2. Close any Windows Explorer windows showing the build folder" -ForegroundColor White
Write-Host "  3. Run this script again" -ForegroundColor White
Write-Host "  4. Or use: ./gradlew build -x clean (skip clean task)" -ForegroundColor White
Write-Host ""
Write-Host "=== Cleanup Complete ===" -ForegroundColor Cyan

