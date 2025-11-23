# Integrated Package Renaming Script: com.geoknoesis.vericore -> com.trustweave
# Usage: Run from project root with .\integrated-rename-package.ps1

param(
    [string]$OldPackage = "com.geoknoesis.vericore",
    [string]$NewPackage = "com.trustweave",
    [switch]$SkipBackup = $false,
    [switch]$ForceCleanup = $false
)

# Configuration
$OldPathForward = $OldPackage.Replace(".", "/")
$NewPathForward = $NewPackage.Replace(".", "/")
$OldPathWindows = $OldPackage.Replace(".", "\")
$NewPathWindows = $NewPackage.Replace(".", "\")

Write-Host "Starting integrated package rename: $OldPackage -> $NewPackage" -ForegroundColor Blue
Write-Host "Current directory: $(Get-Location)" -ForegroundColor Cyan

function Write-Success {
    param([string]$Message)
    Write-Host "✅ $Message" -ForegroundColor Green
}

function Write-Warning {
    param([string]$Message)
    Write-Host "⚠️ $Message" -ForegroundColor Yellow
}

function Write-Error {
    param([string]$Message)
    Write-Host "❌ $Message" -ForegroundColor Red
}

function Should-Exclude {
    param([string]$Path)
    $ExcludePatterns = @("*.lock", "build", ".gradle", "node_modules", "target", ".git", "*.tmp")
    foreach ($pattern in $ExcludePatterns) {
        if ($Path -like "*$pattern*") {
            return $true
        }
    }
    return $false
}

# Check if we're in the project root
if (-not (Test-Path "build.gradle.kts") -or -not (Test-Path "settings.gradle.kts")) {
    Write-Error "Please run this script from the project root directory"
    exit 1
}

# Clean Gradle build first
Write-Success "Cleaning Gradle build..."
try {
    if (Test-Path "gradlew.bat") {
        & .\gradlew.bat clean 2>$null
    } else {
        & .\gradlew clean 2>$null
    }
    Start-Sleep -Seconds 2
} catch {
    Write-Warning "Could not run gradle clean, proceeding anyway..."
}

# Create backup (unless skipped)
if (-not $SkipBackup) {
    Write-Success "Creating backup..."
    $ProjectName = Split-Path (Get-Location) -Leaf
    $BackupDir = "..\$ProjectName-backup-$(Get-Date -Format 'yyyyMMdd-HHmmss')"

    New-Item -ItemType Directory -Path $BackupDir -Force | Out-Null
    $CopyCount = 0

    Get-ChildItem -Path "." | ForEach-Object {
        if (-not (Should-Exclude $_.Name)) {
            try {
                $DestPath = Join-Path $BackupDir $_.Name
                if ($_.PSIsContainer) {
                    robocopy $_.FullName $DestPath /E /XD build .gradle node_modules target /XF *.lock *.tmp /NFL /NDL /NJH /NJS | Out-Null
                    if ($LASTEXITCODE -le 1) { $CopyCount++ }
                } else {
                    Copy-Item -Path $_.FullName -Destination $DestPath -Force
                    $CopyCount++
                }
            } catch {
                Write-Warning "Could not backup $($_.Name)"
            }
        }
    }
    Write-Success "Backup created at $BackupDir (copied: $CopyCount items)"
}

# Update package declarations
Write-Success "Updating package declarations..."
$PackageUpdateCount = 0

Get-ChildItem -Path "." -Include "*.kt", "*.java" -Recurse | Where-Object {
    -not (Should-Exclude $_.FullName)
} | ForEach-Object {
    $content = Get-Content $_.FullName -Raw -ErrorAction SilentlyContinue
    if ($content -and ($content -match "package\s+com\.geoknoesis\.vericore")) {
        $content = $content -replace "package\s+com\.geoknoesis\.vericore", "package com.trustweave"
        $content = $content -replace "package\s+com\.geoknoesis\.vericore\.", "package com.trustweave."
        Set-Content -Path $_.FullName -Value $content -NoNewline
        $PackageUpdateCount++
    }
}
Write-Host "Updated package declarations in $PackageUpdateCount files" -ForegroundColor Cyan

# Update import statements
Write-Success "Updating import statements..."
$ImportUpdateCount = 0

Get-ChildItem -Path "." -Include "*.kt", "*.java", "*.md", "*.gradle.kts", "*.gradle" -Recurse | Where-Object {
    -not (Should-Exclude $_.FullName)
} | ForEach-Object {
    $content = Get-Content $_.FullName -Raw -ErrorAction SilentlyContinue
    if ($content -and ($content -match "com\.geoknoesis\.vericore")) {
        $content = $content -replace "com\.geoknoesis\.vericore", "com.trustweave"
        $content = $content -replace "geoknoesis\.vericore", "trustweave"
        $content = $content -replace "geoknoesis/vericore", "trustweave"
        Set-Content -Path $_.FullName -Value $content -NoNewline
        $ImportUpdateCount++
    }
}
Write-Host "Updated imports in $ImportUpdateCount files" -ForegroundColor Cyan

# Update build.gradle.kts group declarations
Write-Success "Updating build.gradle.kts group declarations..."
$GroupUpdateCount = 0

Get-ChildItem -Path "." -Include "build.gradle.kts", "*.gradle" -Recurse | Where-Object {
    -not (Should-Exclude $_.FullName)
} | ForEach-Object {
    $content = Get-Content $_.FullName -Raw -ErrorAction SilentlyContinue
    if ($content -and ($content -match 'group\s*=\s*"com\.geoknoesis\.vericore')) {
        $content = $content -replace 'group\s*=\s*"com\.geoknoesis\.vericore([^"]*)"', 'group = "com.trustweave$1"'
        Set-Content -Path $_.FullName -Value $content -NoNewline
        $GroupUpdateCount++
    }
}
Write-Host "Updated group in $GroupUpdateCount build files" -ForegroundColor Cyan

# Move files from old directories to new ones
Write-Success "Moving files from old package structure to new structure..."
$FilesMovedCount = 0
$DirectoriesCreated = 0

$OldPackageDirs = Get-ChildItem -Path "." -Directory -Recurse -ErrorAction SilentlyContinue | Where-Object {
    $_.FullName -like "*com\geoknoesis*" -and 
    $_.FullName -notlike "*trustweave*" -and
    -not (Should-Exclude $_.FullName)
}

Write-Host "Found $($OldPackageDirs.Count) old package directories to process" -ForegroundColor Cyan

foreach ($OldDir in $OldPackageDirs) {
    $OldDirPath = $OldDir.FullName
    
    # Find all files in this directory (non-recursive for this iteration)
    $FilesInDir = Get-ChildItem -Path $OldDirPath -File -ErrorAction SilentlyContinue
    
    if ($FilesInDir) {
        Write-Host "  Processing files in: $OldDirPath" -ForegroundColor Yellow
        
        foreach ($File in $FilesInDir) {
            $OldFilePath = $File.FullName
            
            # Calculate new file path by replacing the package structure
            $RelativePath = $OldFilePath.Substring((Get-Location).Path.Length + 1)
            
            # Transform the path: com\geoknoesis\vericore -> com\trustweave
            $NewRelativePath = $RelativePath -replace "com\\geoknoesis\\vericore", "com\\trustweave"
            $NewRelativePath = $NewRelativePath -replace "com/geoknoesis/vericore", "com/trustweave"
            $NewRelativePath = $NewRelativePath -replace "com\\geoknoesis", "com\\trustweave"  
            $NewRelativePath = $NewRelativePath -replace "com/geoknoesis", "com/trustweave"
            
            $NewFilePath = Join-Path (Get-Location) $NewRelativePath
            
            if ($OldFilePath -ne $NewFilePath) {
                try {
                    # Create destination directory
                    $NewFileDir = Split-Path $NewFilePath -Parent
                    if (-not (Test-Path $NewFileDir)) {
                        New-Item -ItemType Directory -Path $NewFileDir -Force | Out-Null
                        Write-Host "    Created: $NewFileDir" -ForegroundColor Green
                        $DirectoriesCreated++
                    }
                    
                    # Move the file
                    if (Test-Path $NewFilePath) {
                        Write-Warning "    Target exists, skipping: $($File.Name)"
                    } else {
                        Move-Item -Path $OldFilePath -Destination $NewFilePath -Force
                        Write-Host "    Moved: $($File.Name)" -ForegroundColor Green
                        $FilesMovedCount++
                    }
                } catch {
                    Write-Warning "    Failed to move $($File.Name): $($_.Exception.Message)"
                }
            }
        }
    }
}

Write-Host "Files moved: $FilesMovedCount" -ForegroundColor Cyan
Write-Host "Directories created: $DirectoriesCreated" -ForegroundColor Cyan

# INTEGRATED AGGRESSIVE DIRECTORY CLEANUP
Write-Success "Aggressive cleanup of old empty directories..."

$MaxPasses = 20
$TotalDirsRemoved = 0

for ($pass = 1; $pass -le $MaxPasses; $pass++) {
    Write-Host "  Cleanup pass $pass..." -ForegroundColor Gray
    
    # Get current list of old directories (deepest first)
    $CurrentOldDirs = Get-ChildItem -Path "." -Directory -Recurse -ErrorAction SilentlyContinue | Where-Object {
        $_.FullName -like "*geoknoesis*" -and 
        $_.FullName -notlike "*trustweave*" -and
        -not (Should-Exclude $_.FullName)
    } | Sort-Object { $_.FullName.Split('\').Count } -Descending
    
    if ($CurrentOldDirs.Count -eq 0) {
        Write-Host "    No more old directories found" -ForegroundColor Gray
        break
    }
    
    $DirsRemovedThisPass = 0
    
    foreach ($DirToRemove in $CurrentOldDirs) {
        $DirPath = $DirToRemove.FullName
        
        try {
            # Method 1: Try PowerShell Remove-Item
            Remove-Item -Path $DirPath -Recurse -Force -ErrorAction Stop
            Write-Host "    ✅ Removed: $DirPath" -ForegroundColor Green
            $DirsRemovedThisPass++
            $TotalDirsRemoved++
        } catch {
            # Method 2: Try CMD rmdir
            try {
                & cmd /c "rmdir /s /q `"$DirPath`"" 2>$null
                
                # Check if it's gone
                if (-not (Test-Path $DirPath)) {
                    Write-Host "    ✅ CMD Removed: $DirPath" -ForegroundColor Green
                    $DirsRemovedThisPass++
                    $TotalDirsRemoved++
                } else {
                    # Method 3: Try individual file deletion
                    $FilesInDir = Get-ChildItem -Path $DirPath -File -Recurse -Force -ErrorAction SilentlyContinue
                    
                    if ($FilesInDir) {
                        foreach ($File in $FilesInDir) {
                            try {
                                & attrib -r -s -h "$($File.FullName)" 2>$null
                                Remove-Item -Path $File.FullName -Force -ErrorAction Stop
                            } catch {
                                # File is really stuck
                            }
                        }
                        
                        # Try to remove directory again
                        try {
                            Remove-Item -Path $DirPath -Recurse -Force -ErrorAction Stop
                            Write-Host "    ✅ Finally removed: $DirPath" -ForegroundColor Green
                            $DirsRemovedThisPass++
                            $TotalDirsRemoved++
                        } catch {
                            Write-Host "    ❌ Stubborn directory: $DirPath" -ForegroundColor DarkRed
                        }
                    } else {
                        # Empty directory that won't delete
                        Write-Host "    ❌ Empty but stubborn: $DirPath" -ForegroundColor DarkRed
                    }
                }
            } catch {
                Write-Host "    ❌ Cannot remove: $DirPath" -ForegroundColor DarkRed
            }
        }
    }
    
    Write-Host "    Pass $pass removed: $DirsRemovedThisPass directories" -ForegroundColor Gray
    
    if ($DirsRemovedThisPass -eq 0) {
        Write-Host "    No progress made, stopping cleanup passes" -ForegroundColor Gray
        break
    }
}

Write-Host "Total directories removed: $TotalDirsRemoved" -ForegroundColor Cyan

# Force cleanup option for remaining directories
$RemainingOldDirs = Get-ChildItem -Path "." -Directory -Recurse -ErrorAction SilentlyContinue | Where-Object {
    $_.FullName -like "*geoknoesis*" -and 
    $_.FullName -notlike "*trustweave*" -and
    -not (Should-Exclude $_.FullName)
}

if ($RemainingOldDirs.Count -gt 0 -and ($ForceCleanup -or $pass -eq $MaxPasses)) {
    Write-Warning "Attempting force cleanup of $($RemainingOldDirs.Count) remaining directories..."
    
    if (-not $ForceCleanup) {
        $confirmation = Read-Host "Force delete remaining directories? (y/N)"
        if ($confirmation -ne "y" -and $confirmation -ne "Y") {
            Write-Host "Skipping force cleanup" -ForegroundColor Yellow
        } else {
            $ForceCleanup = $true
        }
    }
    
    if ($ForceCleanup) {
        foreach ($Dir in $RemainingOldDirs) {
            try {
                Write-Host "  Force deleting: $($Dir.FullName)" -ForegroundColor Red
                
                # Try multiple methods
                & cmd /c "rmdir /s /q `"$($Dir.FullName)`"" 2>$null
                
                if (Test-Path $Dir.FullName) {
                    # Change attributes and try again
                    & attrib -r -s -h "$($Dir.FullName)\*.*" /s /d 2>$null
                    Remove-Item -Path $Dir.FullName -Recurse -Force -ErrorAction SilentlyContinue
                }
                
                if (-not (Test-Path $Dir.FullName)) {
                    Write-Host "  ✅ Force deleted: $($Dir.FullName)" -ForegroundColor Green
                    $TotalDirsRemoved++
                } else {
                    Write-Host "  ❌ Still exists: $($Dir.FullName)" -ForegroundColor Red
                }
            } catch {
                Write-Host "  ❌ Force delete failed: $($Dir.FullName)" -ForegroundColor Red
            }
        }
    }
}

# Final verification
Write-Success "Final verification..."

$FinalRemainingDirs = Get-ChildItem -Path "." -Directory -Recurse -ErrorAction SilentlyContinue | Where-Object {
    $_.FullName -like "*geoknoesis*" -and 
    $_.FullName -notlike "*trustweave*" -and
    -not (Should-Exclude $_.FullName)
}

$NewDirs = Get-ChildItem -Path "." -Directory -Recurse -ErrorAction SilentlyContinue | Where-Object {
    $_.FullName -like "*trustweave*" -and -not (Should-Exclude $_.FullName)
}

# Check for remaining old references in files
$RemainingFileRefs = @()
Get-ChildItem -Path "." -Include "*.kt", "*.java", "*.gradle.kts", "*.gradle" -Recurse -ErrorAction SilentlyContinue | Where-Object {
    -not (Should-Exclude $_.FullName)
} | ForEach-Object {
    $content = Get-Content $_.FullName -Raw -ErrorAction SilentlyContinue
    if ($content -and ($content -match "com\.geoknoesis\.vericore|geoknoesis\.vericore")) {
        $RemainingFileRefs += $_.FullName
    }
}

# Create comprehensive summary report
$ReportContent = @"
Integrated Package Rename Summary
================================
Date: $(Get-Date)
Old Package: $OldPackage
New Package: $NewPackage
Backup Created: $(if ($SkipBackup) { "No (skipped)" } else { "Yes at $BackupDir" })

FILE CHANGES:
- Updated package declarations in $PackageUpdateCount source files
- Updated import statements in $ImportUpdateCount files  
- Updated build.gradle.kts group declarations in $GroupUpdateCount files

DIRECTORY CHANGES:
- Files moved from old structure: $FilesMovedCount files
- New directories created: $DirectoriesCreated directories
- Old directories removed: $TotalDirsRemoved directories

FINAL STATUS:
- New package directories found: $($NewDirs.Count)
- Old directories remaining: $($FinalRemainingDirs.Count)
- Files with old package references: $($RemainingFileRefs.Count)

$(if ($FinalRemainingDirs.Count -gt 0) {
"REMAINING OLD DIRECTORIES:
$($FinalRemainingDirs | ForEach-Object { "- $($_.FullName)" } | Out-String)"
} else { "✅ ALL OLD DIRECTORIES SUCCESSFULLY REMOVED!" })

$(if ($RemainingFileRefs.Count -gt 0) {
"FILES WITH OLD REFERENCES (first 10):
$($RemainingFileRefs | Select-Object -First 10 | ForEach-Object { "- $_" } | Out-String)"
} else { "✅ NO OLD PACKAGE REFERENCES FOUND IN FILES!" })

NEW PACKAGE STRUCTURE (first 10):
$($NewDirs | Select-Object -First 10 | ForEach-Object { "- $($_.FullName)" } | Out-String)

NEXT STEPS:
1. Review changes: git status
2. Test compilation: .\gradlew clean build
3. Run tests: .\gradlew test
$(if ($FinalRemainingDirs.Count -gt 0) { "4. Manually clean remaining directories (may need to close IntelliJ/restart)" } else { "" })
$(if ($RemainingFileRefs.Count -gt 0) { "5. Review and fix remaining file references" } else { "" })
6. Commit: git add . && git commit -m "Rename package from $OldPackage to $NewPackage"
"@

Set-Content -Path "package-rename-report.txt" -Value $ReportContent
Write-Success "Comprehensive report saved to package-rename-report.txt"

# Final summary
Write-Host ""
Write-Host "🎉 INTEGRATED PACKAGE RENAME COMPLETED! 🎉" -ForegroundColor Blue
Write-Host ""
Write-Host "SUMMARY:" -ForegroundColor Yellow
Write-Host "✅ Package declarations updated: $PackageUpdateCount files" -ForegroundColor Green
Write-Host "✅ Import statements updated: $ImportUpdateCount files" -ForegroundColor Green
Write-Host "✅ Group declarations updated: $GroupUpdateCount files" -ForegroundColor Green
Write-Host "✅ Files moved: $FilesMovedCount files" -ForegroundColor Green
Write-Host "✅ New directories created: $DirectoriesCreated directories" -ForegroundColor Green
Write-Host "✅ Old directories removed: $TotalDirsRemoved directories" -ForegroundColor Green

if ($FinalRemainingDirs.Count -eq 0) {
    Write-Host "✅ ALL OLD DIRECTORIES CLEANED UP!" -ForegroundColor Green
} else {
    Write-Host "⚠️  Old directories remaining: $($FinalRemainingDirs.Count)" -ForegroundColor Yellow
}

if ($RemainingFileRefs.Count -eq 0) {
    Write-Host "✅ NO OLD PACKAGE REFERENCES IN FILES!" -ForegroundColor Green
} else {
    Write-Host "⚠️  Files with old references: $($RemainingFileRefs.Count)" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "NEXT STEPS:" -ForegroundColor Yellow
Write-Host "1. 📋 Review: Get-Content package-rename-report.txt" -ForegroundColor Cyan
Write-Host "2. 🔧 Test build: .\gradlew clean build" -ForegroundColor Cyan
Write-Host "3. 🧪 Run tests: .\gradlew test" -ForegroundColor Cyan
Write-Host "4. 📝 Commit: git add . && git commit -m 'Rename package from $OldPackage to $NewPackage'" -ForegroundColor Cyan

if ($FinalRemainingDirs.Count -gt 0) {
    Write-Host ""
    Write-Host "MANUAL CLEANUP FOR REMAINING DIRECTORIES:" -ForegroundColor Red
    Write-Host "- Close IntelliJ IDEA completely" -ForegroundColor Yellow
    Write-Host "- Close all file explorers" -ForegroundColor Yellow
    Write-Host "- Run PowerShell as Administrator" -ForegroundColor Yellow
    Write-Host "- Or restart your computer and try again" -ForegroundColor Yellow
}

if (-not $SkipBackup) {
    Write-Host ""
    Write-Host "💾 Backup available at: $BackupDir" -ForegroundColor Green
}
