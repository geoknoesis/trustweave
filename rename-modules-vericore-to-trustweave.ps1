# Module Renaming Script: vericore -> trustweave
# This script renames module directories and updates all references

param(
    [switch]$DryRun = $false
)

Write-Host "=== MODULE RENAME: vericore -> trustweave ===" -ForegroundColor Blue
Write-Host ""

if ($DryRun) {
    Write-Host "DRY RUN MODE - No files will be modified" -ForegroundColor Yellow
    Write-Host ""
}

# Check we're in the right place
if (-not (Test-Path "build.gradle.kts")) {
    Write-Host "Run from project root directory" -ForegroundColor Red
    exit 1
}

# Module mappings: old name -> new name
$moduleMappings = @{
    "vericore" = "trustweave"
    "vericore-core" = "trustweave-core"
    "vericore-spi" = "trustweave-spi"
    "vericore-json" = "trustweave-json"
    "vericore-trust" = "trustweave-trust"
    "vericore-testkit" = "trustweave-testkit"
    "vericore-contract" = "trustweave-contract"
    "vericore-did" = "trustweave-did"
    "vericore-kms" = "trustweave-kms"
    "vericore-anchor" = "trustweave-anchor"
    "vericore-all" = "trustweave-all"
    "vericore-bom" = "trustweave-bom"
    "vericore-examples" = "trustweave-examples"
}

# Step 1: Update settings.gradle.kts
Write-Host "Step 1: Updating settings.gradle.kts..." -ForegroundColor Green
$settingsFile = "settings.gradle.kts"
if (Test-Path $settingsFile) {
    $content = Get-Content $settingsFile -Raw
    $originalContent = $content

    # Update rootProject.name
    $content = $content -replace 'rootProject\.name\s*=\s*"vericore"', 'rootProject.name = "trustweave"'

    # Update module includes (e.g., include("core:vericore-core"))
    foreach ($oldName in $moduleMappings.Keys) {
        $newName = $moduleMappings[$oldName]
        # Match include("core:vericore-core") or include("distribution:vericore-all")
        $content = $content -replace "include\(`"([^`"]*):$oldName`"\)", "include(`"`$1:$newName`")"
    }

    # Update project directory mappings (e.g., project(":core:vericore-core"))
    foreach ($oldName in $moduleMappings.Keys) {
        $newName = $moduleMappings[$oldName]
        # Match project(":core:vericore-core").projectDir = file("core/vericore-core")
        $content = $content -replace "project\(`":([^`"]*):$oldName`"\)", "project(`":`$1:$newName`")"
        $content = $content -replace "file\(`"([^`"]*)/$oldName`"\)", "file(`"`$1/$newName`")"
    }

    if ($content -ne $originalContent) {
        if ($DryRun) {
            Write-Host "  [DRY RUN] Would update: $settingsFile" -ForegroundColor Cyan
        } else {
            Set-Content -Path $settingsFile -Value $content -NoNewline
            Write-Host "  Updated: $settingsFile" -ForegroundColor Green
        }
    }
}

# Step 2: Update build.gradle.kts files
Write-Host "Step 2: Updating build.gradle.kts files..." -ForegroundColor Green
$buildFiles = Get-ChildItem -Recurse -Include "build.gradle.kts" | Where-Object {
    $_.FullName -notlike "*\build\*" -and
    $_.FullName -notlike "*\.gradle\*" -and
    $_.FullName -notlike "*\node_modules\*"
}

$buildFileCount = 0
foreach ($file in $buildFiles) {
    $content = Get-Content $file.FullName -Raw -ErrorAction SilentlyContinue
    if ($null -eq $content) { continue }

    $originalContent = $content
    $modified = $false

    foreach ($oldName in $moduleMappings.Keys) {
        $newName = $moduleMappings[$oldName]
        # Update project references like :vericore-core, :vericore-spi, etc.
        if ($content -match ":$oldName") {
            $content = $content -replace ":$oldName", ":$newName"
            $modified = $true
        }
        # Update path references like /vericore-core, /vericore-spi, etc.
        if ($content -match "/$oldName") {
            $content = $content -replace "/$oldName", "/$newName"
            $modified = $true
        }
        # Update project name references
        if ($content -match "project\.name\s*==\s*`"$oldName`"") {
            $content = $content -replace "project\.name\s*==\s*`"$oldName`"", "project.name == `"$newName`""
            $modified = $true
        }
    }

    if ($modified) {
        $buildFileCount++
        if ($DryRun) {
            Write-Host "  [DRY RUN] Would update: $($file.FullName)" -ForegroundColor Cyan
        } else {
            Set-Content -Path $file.FullName -Value $content -NoNewline -ErrorAction Stop
            Write-Host "  Updated: $($file.Name)" -ForegroundColor Green
        }
    }
}
Write-Host "  Processed $buildFileCount build files" -ForegroundColor Green

# Step 3: Rename directories
Write-Host "Step 3: Renaming module directories..." -ForegroundColor Green
$directoryMappings = @{
    "core\vericore-core" = "core\trustweave-core"
    "core\vericore-spi" = "core\trustweave-spi"
    "core\vericore-json" = "core\trustweave-json"
    "core\vericore-trust" = "core\trustweave-trust"
    "core\vericore-testkit" = "core\trustweave-testkit"
    "core\vericore-contract" = "core\trustweave-contract"
    "did\vericore-did" = "did\trustweave-did"
    "kms\vericore-kms" = "kms\trustweave-kms"
    "chains\vericore-anchor" = "chains\trustweave-anchor"
    "distribution\vericore-all" = "distribution\trustweave-all"
    "distribution\vericore-bom" = "distribution\trustweave-bom"
    "distribution\vericore-examples" = "distribution\trustweave-examples"
}

$dirCount = 0
foreach ($oldDir in $directoryMappings.Keys) {
    $newDir = $directoryMappings[$oldDir]
    if (Test-Path $oldDir) {
        if ($DryRun) {
            Write-Host "  [DRY RUN] Would rename: $oldDir -> $newDir" -ForegroundColor Cyan
        } else {
            try {
                # Check if target already exists
                if (Test-Path $newDir) {
                    Write-Host "  Warning: $newDir already exists, skipping $oldDir" -ForegroundColor Yellow
                    continue
                }
                Move-Item -Path $oldDir -Destination $newDir -Force -ErrorAction Stop
                Write-Host "  Renamed: $oldDir -> $newDir" -ForegroundColor Green
                $dirCount++
            } catch {
                Write-Host "  Error renaming $oldDir : $_" -ForegroundColor Red
            }
        }
    } else {
        if (-not $DryRun) {
            Write-Host "  Warning: $oldDir does not exist" -ForegroundColor Yellow
        }
    }
}
Write-Host "  Renamed $dirCount directories" -ForegroundColor Green

# Step 4: Update root build.gradle.kts
Write-Host "Step 4: Updating root build.gradle.kts..." -ForegroundColor Green
$rootBuildFile = "build.gradle.kts"
if (Test-Path $rootBuildFile) {
    $content = Get-Content $rootBuildFile -Raw
    $originalContent = $content

    foreach ($oldName in $moduleMappings.Keys) {
        $newName = $moduleMappings[$oldName]
        if ($content -match $oldName) {
            $content = $content -replace $oldName, $newName
        }
    }

    if ($content -ne $originalContent) {
        if ($DryRun) {
            Write-Host "  [DRY RUN] Would update: $rootBuildFile" -ForegroundColor Cyan
        } else {
            Set-Content -Path $rootBuildFile -Value $content -NoNewline
            Write-Host "  Updated: $rootBuildFile" -ForegroundColor Green
        }
    }
}

Write-Host ""
Write-Host "Summary:" -ForegroundColor Blue
Write-Host "  Build files processed: $buildFileCount" -ForegroundColor Green
Write-Host "  Directories to rename: $($directoryMappings.Count)" -ForegroundColor Green

if ($DryRun) {
    Write-Host ""
    Write-Host "Run without -DryRun to apply changes" -ForegroundColor Cyan
} else {
    Write-Host ""
    Write-Host "RENAME COMPLETED!" -ForegroundColor Blue
    Write-Host "Next: Run .\gradlew.bat clean build to verify" -ForegroundColor Cyan
}

Write-Host ""
Write-Host "Done!" -ForegroundColor Blue

