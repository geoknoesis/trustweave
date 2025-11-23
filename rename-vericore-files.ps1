# PowerShell script to rename all VeriCore files to TrustWeave
# This script renames files and updates file content references

Write-Host "=== RENAMING VeriCore FILES TO TrustWeave ===" -ForegroundColor Blue

$filesToRename = @(
    @{Old = "distribution\vericore-all\src\main\kotlin\com\trustweave\VeriCore.kt"; New = "distribution\vericore-all\src\main\kotlin\com\trustweave\TrustWeave.kt"},
    @{Old = "distribution\vericore-all\src\main\kotlin\com\trustweave\VeriCoreDefaults.kt"; New = "distribution\vericore-all\src\main\kotlin\com\trustweave\TrustWeaveDefaults.kt"},
    @{Old = "distribution\vericore-all\src\main\kotlin\com\trustweave\VeriCoreExtensions.kt"; New = "distribution\vericore-all\src\main\kotlin\com\trustweave\TrustWeaveExtensions.kt"},
    @{Old = "distribution\vericore-all\src\test\kotlin\com\trustweave\VeriCoreTest.kt"; New = "distribution\vericore-all\src\test\kotlin\com\trustweave\TrustWeaveTest.kt"},
    @{Old = "core\vericore-core\src\main\kotlin\com\trustweave\core\VeriCoreException.kt"; New = "core\vericore-core\src\main\kotlin\com\trustweave\core\TrustWeaveException.kt"},
    @{Old = "core\vericore-core\src\main\kotlin\com\trustweave\core\VeriCoreErrors.kt"; New = "core\vericore-core\src\main\kotlin\com\trustweave\core\TrustWeaveErrors.kt"},
    @{Old = "core\vericore-core\src\main\kotlin\com\trustweave\core\VeriCoreConstants.kt"; New = "core\vericore-core\src\main\kotlin\com\trustweave\core\TrustWeaveConstants.kt"},
    @{Old = "core\vericore-core\src\test\kotlin\com\trustweave\core\VeriCoreExceptionTest.kt"; New = "core\vericore-core\src\test\kotlin\com\trustweave\core\TrustWeaveExceptionTest.kt"},
    @{Old = "core\vericore-core\src\test\kotlin\com\trustweave\core\VeriCoreConstantsTest.kt"; New = "core\vericore-core\src\test\kotlin\com\trustweave\core\TrustWeaveConstantsTest.kt"},
    @{Old = "core\vericore-testkit\src\test\kotlin\com\trustweave\testkit\VeriCoreTestFixtureTest.kt"; New = "core\vericore-testkit\src\test\kotlin\com\trustweave\testkit\TrustWeaveTestFixtureTest.kt"},
    @{Old = "core\vericore-testkit\src\test\kotlin\com\trustweave\testkit\VeriCoreTestFixtureExample.kt"; New = "core\vericore-testkit\src\test\kotlin\com\trustweave\testkit\TrustWeaveTestFixtureExample.kt"},
    @{Old = "core\vericore-testkit\src\test\kotlin\com\trustweave\testkit\VeriCoreIntegrationTest.kt"; New = "core\vericore-testkit\src\test\kotlin\com\trustweave\testkit\TrustWeaveIntegrationTest.kt"}
)

foreach ($file in $filesToRename) {
    if (Test-Path $file.Old) {
        Write-Host "Renaming: $($file.Old) -> $($file.New)" -ForegroundColor Yellow
        $directory = Split-Path -Parent $file.New
        if (-not (Test-Path $directory)) {
            New-Item -ItemType Directory -Path $directory -Force | Out-Null
        }
        Move-Item -Path $file.Old -Destination $file.New -Force
    } else {
        Write-Host "File not found: $($file.Old)" -ForegroundColor Red
    }
}

Write-Host "`n=== FILE RENAMING COMPLETE ===" -ForegroundColor Green

