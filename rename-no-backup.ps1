# Simple Package Rename Script (No Backup)

param(
    [switch]$ForceCleanup = $false
)

Write-Host "=== PACKAGE RENAME: com.geoknoesis.vericore -> com.trustweave ===" -ForegroundColor Blue

# Check we're in the right place
if (-not (Test-Path "build.gradle.kts")) {
    Write-Host "❌ Run from project root directory" -ForegroundColor Red
    exit 1
}

Write-Host "📝 Updating package declarations..." -ForegroundColor Green
$count1 = 0
Get-ChildItem -Recurse -Include "*.kt", "*.java" | ForEach-Object {
    $content = Get-Content $_.FullName -Raw
    if ($content -match "package\s+com\.geoknoesis\.vericore") {
        $content = $content -replace "package com\.geoknoesis\.vericore", "package com.trustweave"
        Set-Content -Path $_.FullName -Value $content -NoNewline
        $count1++
    }
}
Write-Host "✅ Updated $count1 package declarations" -ForegroundColor Green

Write-Host "📝 Updating imports and references..." -ForegroundColor Green
$count2 = 0
Get-ChildItem -Recurse -Include "*.kt", "*.java", "*.gradle.kts", "*.md", "*.txt", "*.json", "*.yaml", "*.yml" | ForEach-Object {
    $content = Get-Content $_.FullName -Raw
    if ($content -match "com\.geoknoesis\.vericore") {
        $content = $content -replace "com\.geoknoesis\.vericore", "com.trustweave"
        $content = $content -replace "geoknoesis\.vericore", "trustweave"
        Set-Content -Path $_.FullName -Value $content -NoNewline
        $count2++
    }
}
Write-Host "✅ Updated $count2 files with imports/references" -ForegroundColor Green

Write-Host "📝 Updating build files (group declarations)..." -ForegroundColor Green
$count3 = 0
Get-ChildItem -Recurse -Include "build.gradle.kts" | ForEach-Object {
    $content = Get-Content $_.FullName -Raw
    if ($content -match 'group\s*=\s*"com\.geoknoesis\.vericore') {
        # Match all variations: com.geoknoesis.vericore, com.geoknoesis.vericore.integrations, etc.
        $content = $content -replace 'group\s*=\s*"com\.geoknoesis\.vericore([^"]*)"', 'group = "com.trustweave$1"'
        Set-Content -Path $_.FullName -Value $content -NoNewline
        $count3++
    }
}
Write-Host "✅ Updated $count3 build files" -ForegroundColor Green

Write-Host "📝 Updating META-INF service files..." -ForegroundColor Green
$count4 = 0
# Find and rename META-INF service files (filenames contain package names)
Get-ChildItem -Recurse -File | Where-Object { 
    $_.FullName -match "META-INF\\services\\com\.geoknoesis\.vericore"
} | ForEach-Object {
    $oldPath = $_.FullName
    $newPath = $oldPath -replace "com\.geoknoesis\.vericore", "com.trustweave"
    
    if ($oldPath -ne $newPath) {
        $newDir = Split-Path $newPath -Parent
        if (-not (Test-Path $newDir)) {
            New-Item -ItemType Directory -Path $newDir -Force | Out-Null
        }
        Move-Item -Path $oldPath -Destination $newPath -Force -ErrorAction SilentlyContinue
        if (Test-Path $newPath) {
            $count4++
        }
    }
}
Write-Host "✅ Renamed $count4 META-INF service files" -ForegroundColor Green

Write-Host "📝 Updating META-INF service file contents..." -ForegroundColor Green
$count5 = 0
Get-ChildItem -Recurse -File | Where-Object { 
    $_.FullName -match "META-INF\\services\\"
} | ForEach-Object {
    $content = Get-Content $_.FullName -Raw
    if ($content -match "com\.geoknoesis\.vericore") {
        $content = $content -replace "com\.geoknoesis\.vericore", "com.trustweave"
        Set-Content -Path $_.FullName -Value $content -NoNewline
        $count5++
    }
}
Write-Host "✅ Updated $count5 META-INF service file contents" -ForegroundColor Green

Write-Host "📁 Moving source files..." -ForegroundColor Green
$count6 = 0
# Find all files in com/geoknoesis/vericore directories
$allFiles = Get-ChildItem -Recurse -File -Include "*.kt", "*.java" | Where-Object {
    $_.FullName -match "\\com\\geoknoesis\\vericore"
}

foreach ($file in $allFiles) {
    $oldPath = $file.FullName
    $newPath = $oldPath -replace "\\com\\geoknoesis\\vericore", "\com\trustweave"
    $newPath = $newPath -replace "\\geoknoesis\\vericore", "\trustweave"
    $newPath = $newPath -replace "geoknoesis\\vericore", "trustweave"
    
    if ($oldPath -ne $newPath -and -not (Test-Path $newPath)) {
        $newDir = Split-Path $newPath -Parent
        if (-not (Test-Path $newDir)) {
            New-Item -ItemType Directory -Path $newDir -Force | Out-Null
        }
        Move-Item -Path $oldPath -Destination $newPath -Force -ErrorAction SilentlyContinue
        if (Test-Path $newPath) {
            $count6++
        }
    }
}
Write-Host "✅ Moved $count6 source files" -ForegroundColor Green

Write-Host "🧹 Cleaning empty directories..." -ForegroundColor Green
$count7 = 0
# Remove empty directories, starting from deepest first
for ($i = 1; $i -le 10; $i++) {
    # Find all directories containing geoknoesis (excluding build directories)
    $dirsToRemove = Get-ChildItem -Recurse -Directory | Where-Object { 
        $_.FullName -like "*geoknoesis*" -and 
        $_.FullName -notlike "*build*" -and
        $_.FullName -notlike "*\.git*" -and
        $_.FullName -notlike "*\.gradle*"
    } | Sort-Object { $_.FullName.Length } -Descending
    
    if ($dirsToRemove.Count -eq 0) { break }
    
    foreach ($dir in $dirsToRemove) {
        # Check if directory is empty or only contains empty subdirectories
        $hasFiles = $false
        try {
            $items = Get-ChildItem -Path $dir.FullName -Recurse -File -ErrorAction SilentlyContinue
            if ($items.Count -gt 0) {
                $hasFiles = $true
            }
        } catch {
            $hasFiles = $true
        }
        
        if (-not $hasFiles) {
            try {
                # Try to remove the directory
                Remove-Item -Path $dir.FullName -Recurse -Force -ErrorAction Stop
                $count7++
            } catch {
                # If PowerShell fails, try cmd rmdir
                try {
                    $null = cmd /c "rmdir /s /q `"$($dir.FullName)`"" 2>&1
                    if (-not (Test-Path $dir.FullName)) {
                        $count7++
                    }
                } catch {
                    # Directory might still be in use, skip for now
                }
            }
        }
    }
}

# Force cleanup if requested
if ($ForceCleanup) {
    Write-Host "🔥 Force cleanup remaining directories..." -ForegroundColor Red
    $remaining = Get-ChildItem -Recurse -Directory | Where-Object { 
        $_.FullName -like "*geoknoesis*" -and 
        $_.FullName -notlike "*build*" -and
        $_.FullName -notlike "*\.git*" -and
        $_.FullName -notlike "*\.gradle*"
    } | Sort-Object { $_.FullName.Length } -Descending
    
    foreach ($dir in $remaining) {
        try {
            # Remove read-only attributes first
            Get-ChildItem -Path $dir.FullName -Recurse -Force | ForEach-Object {
                $_.Attributes = $_.Attributes -band (-bnot [System.IO.FileAttributes]::ReadOnly)
            }
            Remove-Item -Path $dir.FullName -Recurse -Force -ErrorAction Stop
            $count7++
        } catch {
            try {
                cmd /c "attrib -r -s -h `"$($dir.FullName)\*.*`" /s /d" 2>&1 | Out-Null
                cmd /c "rmdir /s /q `"$($dir.FullName)`"" 2>&1 | Out-Null
                if (-not (Test-Path $dir.FullName)) {
                    $count7++
                }
            } catch {
                Write-Host "⚠️  Could not remove: $($dir.FullName)" -ForegroundColor Yellow
            }
        }
    }
}

Write-Host "✅ Removed $count7 directories" -ForegroundColor Green

Write-Host ""
Write-Host "🎉 RENAME COMPLETED!" -ForegroundColor Blue
Write-Host "✅ Package declarations: $count1" -ForegroundColor Green
Write-Host "✅ Import/reference files: $count2" -ForegroundColor Green  
Write-Host "✅ Build files: $count3" -ForegroundColor Green
Write-Host "✅ META-INF service files renamed: $count4" -ForegroundColor Green
Write-Host "✅ META-INF service file contents: $count5" -ForegroundColor Green
Write-Host "✅ Source files moved: $count6" -ForegroundColor Green
Write-Host "✅ Directories cleaned: $count7" -ForegroundColor Green

$finalCheck = Get-ChildItem -Recurse -Directory | Where-Object { 
    $_.FullName -like "*geoknoesis*" -and 
    $_.FullName -notlike "*build*" -and
    $_.FullName -notlike "*\.git*" -and
    $_.FullName -notlike "*\.gradle*"
}
if ($finalCheck.Count -eq 0) {
    Write-Host "🎯 ALL OLD DIRECTORIES REMOVED!" -ForegroundColor Green
} else {
    Write-Host "⚠️  $($finalCheck.Count) directories remain:" -ForegroundColor Yellow
    foreach ($dir in $finalCheck) {
        Write-Host "   - $($dir.FullName)" -ForegroundColor Yellow
    }
    Write-Host ""
    Write-Host "💡 Run with -ForceCleanup to attempt aggressive removal" -ForegroundColor Cyan
}

Write-Host ""
Write-Host "Next: .\gradlew clean build" -ForegroundColor Cyan
Write-Host "🏁 Done!" -ForegroundColor Blue
