# Rename chain plugin packages to com.trustweave.chain.*

param(
    [switch]$DryRun = $false
)

Write-Host "=== RENAMING CHAIN PLUGIN PACKAGES ===" -ForegroundColor Blue
Write-Host ""

if ($DryRun) {
    Write-Host "DRY RUN MODE - No files will be modified" -ForegroundColor Yellow
    Write-Host ""
}

# Chain plugin mappings: old package -> new package
$packageMappings = @{
    "com.trustweave.algorand" = "com.trustweave.chain.algorand"
    "com.trustweave.arbitrum" = "com.trustweave.chain.arbitrum"
    "com.trustweave.base" = "com.trustweave.chain.base"
    "com.trustweave.ethereum" = "com.trustweave.chain.ethereum"
    "com.trustweave.ganache" = "com.trustweave.chain.ganache"
    "com.trustweave.indy" = "com.trustweave.chain.indy"
    "com.trustweave.polygon" = "com.trustweave.chain.polygon"
    "com.trustweave.anchor.bitcoin" = "com.trustweave.chain.bitcoin"
    "com.trustweave.anchor.cardano" = "com.trustweave.chain.cardano"
    "com.trustweave.anchor.optimism" = "com.trustweave.chain.optimism"
    "com.trustweave.anchor.starknet" = "com.trustweave.chain.starknet"
    "com.trustweave.anchor.zksync" = "com.trustweave.chain.zksync"
}

# Directory mappings: old path -> new path
$directoryMappings = @{
    "chains\plugins\algorand\src\main\kotlin\com\trustweave\algorand" = "chains\plugins\algorand\src\main\kotlin\com\trustweave\chain\algorand"
    "chains\plugins\algorand\src\test\kotlin\com\trustweave\algorand" = "chains\plugins\algorand\src\test\kotlin\com\trustweave\chain\algorand"
    "chains\plugins\arbitrum\src\main\kotlin\com\trustweave\arbitrum" = "chains\plugins\arbitrum\src\main\kotlin\com\trustweave\chain\arbitrum"
    "chains\plugins\arbitrum\src\test\kotlin\com\trustweave\arbitrum" = "chains\plugins\arbitrum\src\test\kotlin\com\trustweave\chain\arbitrum"
    "chains\plugins\base\src\main\kotlin\com\trustweave\base" = "chains\plugins\base\src\main\kotlin\com\trustweave\chain\base"
    "chains\plugins\base\src\test\kotlin\com\trustweave\base" = "chains\plugins\base\src\test\kotlin\com\trustweave\chain\base"
    "chains\plugins\ethereum\src\main\kotlin\com\trustweave\ethereum" = "chains\plugins\ethereum\src\main\kotlin\com\trustweave\chain\ethereum"
    "chains\plugins\ethereum\src\test\kotlin\com\trustweave\ethereum" = "chains\plugins\ethereum\src\test\kotlin\com\trustweave\chain\ethereum"
    "chains\plugins\ganache\src\main\kotlin\com\trustweave\ganache" = "chains\plugins\ganache\src\main\kotlin\com\trustweave\chain\ganache"
    "chains\plugins\ganache\src\test\kotlin\com\trustweave\ganache" = "chains\plugins\ganache\src\test\kotlin\com\trustweave\chain\ganache"
    "chains\plugins\indy\src\main\kotlin\com\trustweave\indy" = "chains\plugins\indy\src\main\kotlin\com\trustweave\chain\indy"
    "chains\plugins\indy\src\test\kotlin\com\trustweave\indy" = "chains\plugins\indy\src\test\kotlin\com\trustweave\chain\indy"
    "chains\plugins\polygon\src\main\kotlin\com\trustweave\polygon" = "chains\plugins\polygon\src\main\kotlin\com\trustweave\chain\polygon"
    "chains\plugins\polygon\src\test\kotlin\com\trustweave\polygon" = "chains\plugins\polygon\src\test\kotlin\com\trustweave\chain\polygon"
    "chains\plugins\bitcoin\src\main\kotlin\com\trustweave\anchor\bitcoin" = "chains\plugins\bitcoin\src\main\kotlin\com\trustweave\chain\bitcoin"
    "chains\plugins\cardano\src\main\kotlin\com\trustweave\anchor\cardano" = "chains\plugins\cardano\src\main\kotlin\com\trustweave\chain\cardano"
    "chains\plugins\optimism\src\main\kotlin\com\trustweave\anchor\optimism" = "chains\plugins\optimism\src\main\kotlin\com\trustweave\chain\optimism"
    "chains\plugins\starknet\src\main\kotlin\com\trustweave\anchor\starknet" = "chains\plugins\starknet\src\main\kotlin\com\trustweave\chain\starknet"
    "chains\plugins\zksync\src\main\kotlin\com\trustweave\anchor\zksync" = "chains\plugins\zksync\src\main\kotlin\com\trustweave\chain\zksync"
}

$fileCount = 0
$importCount = 0

# Step 1: Update package declarations and imports in all Kotlin files
Write-Host "Step 1: Updating package declarations and imports..." -ForegroundColor Green
Get-ChildItem -Path "chains\plugins" -Recurse -Include "*.kt" | ForEach-Object {
    $file = $_
    $content = Get-Content $file.FullName -Raw -ErrorAction SilentlyContinue
    if ($null -eq $content) { return }
    
    $originalContent = $content
    $modified = $false
    
    # Update package declarations
    foreach ($oldPkg in $packageMappings.Keys) {
        $newPkg = $packageMappings[$oldPkg]
        if ($content -match "package $([regex]::Escape($oldPkg))") {
            $content = $content -replace "package $([regex]::Escape($oldPkg))", "package $newPkg"
            $modified = $true
        }
    }
    
    # Update imports
    foreach ($oldPkg in $packageMappings.Keys) {
        $newPkg = $packageMappings[$oldPkg]
        $escapedOld = [regex]::Escape($oldPkg)
        # Match import statements
        if ($content -match "import $escapedOld") {
            $content = $content -replace "import $escapedOld", "import $newPkg"
            $modified = $true
        }
        # Match import with wildcard
        if ($content -match "import $escapedOld\.") {
            $content = $content -replace "import $escapedOld\.", "import $newPkg."
            $modified = $true
        }
    }
    
    if ($modified) {
        $fileCount++
        if ($DryRun) {
            Write-Host "  [DRY RUN] Would update: $($file.FullName)" -ForegroundColor Cyan
        } else {
            Set-Content -Path $file.FullName -Value $content -NoNewline
            Write-Host "  Updated: $($file.Name)" -ForegroundColor Green
        }
    }
}
Write-Host "  Processed $fileCount files" -ForegroundColor Green

# Step 2: Move directories
Write-Host "Step 2: Moving directories..." -ForegroundColor Green
$dirCount = 0
foreach ($oldDir in $directoryMappings.Keys) {
    $newDir = $directoryMappings[$oldDir]
    if (Test-Path $oldDir) {
        if ($DryRun) {
            Write-Host "  [DRY RUN] Would move: $oldDir -> $newDir" -ForegroundColor Cyan
        } else {
            try {
                # Create parent directory if needed
                $parentDir = Split-Path $newDir -Parent
                if (-not (Test-Path $parentDir)) {
                    New-Item -ItemType Directory -Path $parentDir -Force | Out-Null
                }
                
                # Check if target already exists
                if (Test-Path $newDir) {
                    Write-Host "  Warning: $newDir already exists, skipping $oldDir" -ForegroundColor Yellow
                    continue
                }
                Move-Item -Path $oldDir -Destination $newDir -Force -ErrorAction Stop
                Write-Host "  Moved: $oldDir -> $newDir" -ForegroundColor Green
                $dirCount++
            } catch {
                Write-Host "  Error moving $oldDir : $_" -ForegroundColor Red
            }
        }
    }
}
Write-Host "  Moved $dirCount directories" -ForegroundColor Green

# Step 3: Update imports in other files (examples, tests, etc.)
Write-Host "Step 3: Updating imports in other files..." -ForegroundColor Green
$otherFileCount = 0
Get-ChildItem -Recurse -Include "*.kt" | Where-Object {
    $_.FullName -notlike "*\chains\plugins\*" -and
    $_.FullName -notlike "*\build\*" -and
    $_.FullName -notlike "*\.gradle\*"
} | ForEach-Object {
    $file = $_
    $content = Get-Content $file.FullName -Raw -ErrorAction SilentlyContinue
    if ($null -eq $content) { return }
    
    $originalContent = $content
    $modified = $false
    
    foreach ($oldPkg in $packageMappings.Keys) {
        $newPkg = $packageMappings[$oldPkg]
        $escapedOld = [regex]::Escape($oldPkg)
        if ($content -match "import $escapedOld") {
            $content = $content -replace "import $escapedOld", "import $newPkg"
            $modified = $true
        }
        if ($content -match "import $escapedOld\.") {
            $content = $content -replace "import $escapedOld\.", "import $newPkg."
            $modified = $true
        }
    }
    
    if ($modified) {
        $otherFileCount++
        if ($DryRun) {
            Write-Host "  [DRY RUN] Would update: $($file.FullName)" -ForegroundColor Cyan
        } else {
            Set-Content -Path $file.FullName -Value $content -NoNewline
            Write-Host "  Updated: $($file.Name)" -ForegroundColor Green
        }
    }
}
Write-Host "  Processed $otherFileCount other files" -ForegroundColor Green

Write-Host ""
Write-Host "Summary:" -ForegroundColor Blue
Write-Host "  Chain plugin files updated: $fileCount" -ForegroundColor Green
Write-Host "  Directories moved: $dirCount" -ForegroundColor Green
Write-Host "  Other files updated: $otherFileCount" -ForegroundColor Green

if ($DryRun) {
    Write-Host ""
    Write-Host "Run without -DryRun to apply changes" -ForegroundColor Cyan
} else {
    Write-Host ""
    Write-Host "RENAME COMPLETED!" -ForegroundColor Blue
    Write-Host "Next: Run .\gradlew.bat build to verify" -ForegroundColor Cyan
}

Write-Host ""
Write-Host "Done!" -ForegroundColor Blue

