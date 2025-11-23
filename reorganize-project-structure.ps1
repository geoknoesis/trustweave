# TrustWeave Project Reorganization Script
# This script reorganizes the project to have self-contained domain modules
# with core/ and plugins/ structure

Write-Host "Starting TrustWeave project reorganization..." -ForegroundColor Green

# Step 1: Create credentials domain structure
Write-Host "`nStep 1: Creating credentials domain structure..." -ForegroundColor Yellow
New-Item -ItemType Directory -Force -Path "credentials/core" | Out-Null
New-Item -ItemType Directory -Force -Path "credentials/plugins/proof" | Out-Null
New-Item -ItemType Directory -Force -Path "credentials/plugins/wallet" | Out-Null
New-Item -ItemType Directory -Force -Path "credentials/plugins/status-list" | Out-Null
New-Item -ItemType Directory -Force -Path "credentials/plugins/audit-logging" | Out-Null
New-Item -ItemType Directory -Force -Path "credentials/plugins/metrics" | Out-Null
New-Item -ItemType Directory -Force -Path "credentials/plugins/qr-code" | Out-Null
New-Item -ItemType Directory -Force -Path "credentials/plugins/notifications" | Out-Null
New-Item -ItemType Directory -Force -Path "credentials/plugins/versioning" | Out-Null
New-Item -ItemType Directory -Force -Path "credentials/plugins/backup" | Out-Null
New-Item -ItemType Directory -Force -Path "credentials/plugins/expiration" | Out-Null
New-Item -ItemType Directory -Force -Path "credentials/plugins/analytics" | Out-Null
New-Item -ItemType Directory -Force -Path "credentials/plugins/oidc4vci" | Out-Null
New-Item -ItemType Directory -Force -Path "credentials/plugins/multi-party" | Out-Null
New-Item -ItemType Directory -Force -Path "credentials/plugins/health-checks" | Out-Null
New-Item -ItemType Directory -Force -Path "credentials/plugins/rendering" | Out-Null
New-Item -ItemType Directory -Force -Path "credentials/plugins/didcomm" | Out-Null
New-Item -ItemType Directory -Force -Path "credentials/plugins/chapi" | Out-Null

# Step 2: Move core/trustweave-core to credentials/core
Write-Host "`nStep 2: Moving core/trustweave-core to credentials/core..." -ForegroundColor Yellow
if (Test-Path "core/trustweave-core") {
    Move-Item -Path "core/trustweave-core" -Destination "credentials/core" -Force
    Write-Host "  Moved core/trustweave-core to credentials/core" -ForegroundColor Green
} else {
    Write-Host "  core/trustweave-core not found, skipping..." -ForegroundColor Yellow
}

# Step 3: Move proof plugins
Write-Host "`nStep 3: Moving proof plugins..." -ForegroundColor Yellow
if (Test-Path "core/plugins/bbs-proof") {
    Move-Item -Path "core/plugins/bbs-proof" -Destination "credentials/plugins/proof/bbs" -Force
    Write-Host "  Moved bbs-proof to credentials/plugins/proof/bbs" -ForegroundColor Green
}
if (Test-Path "core/plugins/jwt-proof") {
    Move-Item -Path "core/plugins/jwt-proof" -Destination "credentials/plugins/proof/jwt" -Force
    Write-Host "  Moved jwt-proof to credentials/plugins/proof/jwt" -ForegroundColor Green
}
if (Test-Path "core/plugins/ld-proof") {
    Move-Item -Path "core/plugins/ld-proof" -Destination "credentials/plugins/proof/ld" -Force
    Write-Host "  Moved ld-proof to credentials/plugins/proof/ld" -ForegroundColor Green
}

# Step 4: Move wallet plugins to wallet/plugins
Write-Host "`nStep 4: Moving wallet plugins to wallet/plugins..." -ForegroundColor Yellow
New-Item -ItemType Directory -Force -Path "wallet/plugins" | Out-Null
if (Test-Path "core/plugins/database-wallet") {
    Move-Item -Path "core/plugins/database-wallet" -Destination "wallet/plugins/database" -Force
    Write-Host "  Moved database-wallet to wallet/plugins/database" -ForegroundColor Green
}
if (Test-Path "core/plugins/file-wallet") {
    Move-Item -Path "core/plugins/file-wallet" -Destination "wallet/plugins/file" -Force
    Write-Host "  Moved file-wallet to wallet/plugins/file" -ForegroundColor Green
}
if (Test-Path "core/plugins/cloud-wallet") {
    Move-Item -Path "core/plugins/cloud-wallet" -Destination "wallet/plugins/cloud" -Force
    Write-Host "  Moved cloud-wallet to wallet/plugins/cloud" -ForegroundColor Green
}

# Step 5: Move status-list plugin
Write-Host "`nStep 5: Moving status-list plugin..." -ForegroundColor Yellow
if (Test-Path "core/plugins/database-status-list") {
    Move-Item -Path "core/plugins/database-status-list" -Destination "credentials/plugins/status-list/database" -Force
    Write-Host "  Moved database-status-list to credentials/plugins/status-list/database" -ForegroundColor Green
}

# Step 6: Move other credential plugins
Write-Host "`nStep 6: Moving other credential plugins..." -ForegroundColor Yellow
$pluginMappings = @{
    "audit-logging" = "credentials/plugins/audit-logging"
    "metrics" = "credentials/plugins/metrics"
    "qr-code" = "credentials/plugins/qr-code"
    "notifications" = "credentials/plugins/notifications"
    "credential-versioning" = "credentials/plugins/versioning"
    "credential-backup" = "credentials/plugins/backup"
    "expiration-management" = "credentials/plugins/expiration"
    "analytics" = "credentials/plugins/analytics"
    "oidc4vci" = "credentials/plugins/oidc4vci"
    "multi-party-issuance" = "credentials/plugins/multi-party"
    "health-checks" = "credentials/plugins/health-checks"
    "credential-rendering" = "credentials/plugins/rendering"
    "didcomm" = "credentials/plugins/didcomm"
    "chapi" = "credentials/plugins/chapi"
}

foreach ($mapping in $pluginMappings.GetEnumerator()) {
    $source = "core/plugins/$($mapping.Key)"
    $dest = $mapping.Value
    if (Test-Path $source) {
        Move-Item -Path $source -Destination $dest -Force
        Write-Host "  Moved $($mapping.Key) to $dest" -ForegroundColor Green
    }
}

# Step 7: Promote modules from core/ to top-level
Write-Host "`nStep 7: Promoting modules from core/ to top-level..." -ForegroundColor Yellow
$promotedModules = @(
    @{Source = "core/trustweave-json"; Dest = "json"},
    @{Source = "core/trustweave-trust"; Dest = "trust"},
    @{Source = "core/trustweave-testkit"; Dest = "testkit"},
    @{Source = "core/trustweave-contract"; Dest = "contract"}
)

foreach ($module in $promotedModules) {
    if (Test-Path $module.Source) {
        Move-Item -Path $module.Source -Destination $module.Dest -Force
        Write-Host "  Moved $($module.Source) to $($module.Dest)" -ForegroundColor Green
    } else {
        Write-Host "  $($module.Source) not found, skipping..." -ForegroundColor Yellow
    }
}

# Step 8: Consolidate core/trustweave-spi into credentials/core
Write-Host "`nStep 8: Consolidating core/trustweave-spi into credentials/core..." -ForegroundColor Yellow
if (Test-Path "core/trustweave-spi") {
    $spiSrc = "core/trustweave-spi/src/main/kotlin"
    $coreDest = "credentials/core/src/main/kotlin"
    if (Test-Path $spiSrc) {
        Copy-Item -Path "$spiSrc/*" -Destination $coreDest -Recurse -Force
        Write-Host "  Copied SPI files to credentials/core (manual merge may be needed)" -ForegroundColor Green
        Write-Host "  core/trustweave-spi kept for manual review - delete after consolidation" -ForegroundColor Yellow
    }
}

# Step 9: Clean up empty directories
Write-Host "`nStep 9: Cleaning up empty directories..." -ForegroundColor Yellow
if (Test-Path "core/plugins") {
    $remainingPlugins = Get-ChildItem -Path "core/plugins" -Directory -ErrorAction SilentlyContinue
    if ($null -eq $remainingPlugins -or $remainingPlugins.Count -eq 0) {
        Remove-Item -Path "core/plugins" -Force -ErrorAction SilentlyContinue
        Write-Host "  Removed empty core/plugins directory" -ForegroundColor Green
    } else {
        $names = $remainingPlugins | ForEach-Object { $_.Name }
        Write-Host "  core/plugins still contains: $($names -join ', ')" -ForegroundColor Yellow
    }
}

if (Test-Path "core") {
    $remainingInCore = Get-ChildItem -Path "core" -Directory -ErrorAction SilentlyContinue
    if ($null -eq $remainingInCore) {
        Remove-Item -Path "core" -Force -ErrorAction SilentlyContinue
        Write-Host "  Removed empty core directory" -ForegroundColor Green
    } elseif ($remainingInCore.Count -eq 0) {
        Remove-Item -Path "core" -Force -ErrorAction SilentlyContinue
        Write-Host "  Removed empty core directory" -ForegroundColor Green
    } else {
        $names = $remainingInCore | ForEach-Object { $_.Name }
        Write-Host "  core still contains: $($names -join ', ')" -ForegroundColor Yellow
    }
}

Write-Host "`nProject reorganization complete!" -ForegroundColor Green
Write-Host "`nNext steps:" -ForegroundColor Cyan
Write-Host "  1. Update settings.gradle.kts with new paths" -ForegroundColor White
Write-Host "  2. Update all build.gradle.kts files with new dependencies" -ForegroundColor White
Write-Host "  3. Update import statements across codebase" -ForegroundColor White
Write-Host "  4. Review and manually merge core/trustweave-spi if needed" -ForegroundColor White
Write-Host "  5. Test the build" -ForegroundColor White
