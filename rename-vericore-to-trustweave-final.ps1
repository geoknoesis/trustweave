# Final VeriCore to TrustWeave Renaming Script
# Replaces all remaining references to VeriCore with TrustWeave

param(
    [switch]$DryRun = $false
)

Write-Host "=== FINAL VERICORE TO TRUSTWEAVE RENAME ===" -ForegroundColor Blue
Write-Host ""

if ($DryRun) {
    Write-Host "DRY RUN MODE - No files will be modified" -ForegroundColor Yellow
    Write-Host ""
}

# Check we're in the right place
if (-not (Test-Path "build.gradle.kts")) {
    Write-Host "❌ Run from project root directory" -ForegroundColor Red
    exit 1
}

$totalFiles = 0
$totalReplacements = 0

# Define replacement patterns
$replacements = @(
    @{
        Pattern = '\bVeriCore\b'
        Replacement = 'TrustWeave'
        Description = 'Class/type name VeriCore'
    },
    @{
        Pattern = '\bvericore\b'
        Replacement = 'trustweave'
        Description = 'Variable/parameter name vericore'
    },
    @{
        Pattern = 'VeriCore\.'
        Replacement = 'TrustWeave.'
        Description = 'Static method calls VeriCore.'
    },
    @{
        Pattern = 'initializeVeriCoreServices'
        Replacement = 'initializeTrustWeaveServices'
        Description = 'Function name initializeVeriCoreServices'
    },
    @{
        Pattern = 'VeriCoreExample'
        Replacement = 'TrustWeaveExample'
        Description = 'Example class name VeriCoreExample'
    },
    @{
        Pattern = 'VeriCoreSharedPlugin'
        Replacement = 'TrustWeaveSharedPlugin'
        Description = 'Plugin class name VeriCoreSharedPlugin'
    }
)

# File extensions to process
$fileExtensions = @('*.kt', '*.java', '*.md', '*.txt', '*.gradle.kts', '*.kts', '*.json', '*.yaml', '*.yml', '*.properties')

Write-Host "Processing files..." -ForegroundColor Green
Write-Host ""

foreach ($ext in $fileExtensions) {
    $files = Get-ChildItem -Recurse -Include $ext -ErrorAction SilentlyContinue | Where-Object {
        $_.FullName -notlike "*\build\*" -and
        $_.FullName -notlike "*\.git\*" -and
        $_.FullName -notlike "*\.gradle\*" -and
        $_.FullName -notlike "*\node_modules\*" -and
        $_.FullName -notlike "*\rename-*.ps1" -and
        $_.FullName -notlike "*\final-*.ps1" -and
        $_.FullName -notlike "*\update-*.ps1"
    }
    
    foreach ($file in $files) {
        try {
            $content = Get-Content $file.FullName -Raw -ErrorAction SilentlyContinue
            if ($null -eq $content) { continue }
            
            $originalContent = $content
            $fileModified = $false
            $fileReplacements = 0
            
            foreach ($replacement in $replacements) {
                if ($content -match $replacement.Pattern) {
                    $matches = ([regex]::Matches($content, $replacement.Pattern))
                    $fileReplacements += $matches.Count
                    $content = $content -replace $replacement.Pattern, $replacement.Replacement
                    $fileModified = $true
                }
            }
            
            if ($fileModified) {
                $totalFiles++
                $totalReplacements += $fileReplacements
                
                if ($DryRun) {
                    Write-Host "  [DRY RUN] Would update: $($file.FullName)" -ForegroundColor Cyan
                    Write-Host "    Replacements: $fileReplacements" -ForegroundColor Gray
                } else {
                    Set-Content -Path $file.FullName -Value $content -NoNewline -ErrorAction Stop
                    Write-Host "  Updated: $($file.Name)" -ForegroundColor Green
                    Write-Host "    Replacements: $fileReplacements" -ForegroundColor Gray
                }
            }
        } catch {
            Write-Host "  Error processing $($file.FullName): $_" -ForegroundColor Yellow
        }
    }
}

Write-Host ""
Write-Host "Summary:" -ForegroundColor Blue
Write-Host "  Files processed: $totalFiles" -ForegroundColor Green
Write-Host "  Total replacements: $totalReplacements" -ForegroundColor Green

if ($DryRun) {
    Write-Host ""
    Write-Host "Run without -DryRun to apply changes" -ForegroundColor Cyan
} else {
    Write-Host ""
    Write-Host "RENAME COMPLETED!" -ForegroundColor Blue
    Write-Host "Next: Run tests to verify: .\gradlew.bat test" -ForegroundColor Cyan
}

Write-Host ""
Write-Host "Done!" -ForegroundColor Blue
