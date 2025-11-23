# PowerShell script to rename VeriCore to TrustWeave across the codebase
# This script performs systematic replacements

Write-Host "=== RENAMING VeriCore to TrustWeave ===" -ForegroundColor Blue
Write-Host ""

$files = Get-ChildItem -Path . -Recurse -Include *.kt,*.kts,*.md -Exclude *build*,*\.git* | Where-Object { $_.FullName -notmatch "\\build\\" -and $_.FullName -notmatch "\\.git\\" }

$replacements = @{
    # Class names
    "class VeriCore" = "class TrustWeave"
    "object VeriCore" = "object TrustWeave"
    "data class VeriCore" = "data class TrustWeave"
    "sealed class VeriCore" = "sealed class TrustWeave"
    "open class VeriCore" = "open class TrustWeave"
    "interface VeriCore" = "interface TrustWeave"
    
    # Specific class names
    "VeriCoreContext" = "TrustWeaveContext"
    "VeriCoreConfig" = "TrustWeaveConfig"
    "VeriCoreDefaults" = "TrustWeaveDefaults"
    "VeriCoreException" = "TrustWeaveException"
    "VeriCoreError" = "TrustWeaveError"
    "VeriCoreConstants" = "TrustWeaveConstants"
    "VeriCoreTestFixture" = "TrustWeaveTestFixture"
    
    # Imports
    "import com.trustweave.VeriCore" = "import com.trustweave.TrustWeave"
    "import com.trustweave.core.VeriCoreException" = "import com.trustweave.core.TrustWeaveException"
    "import com.trustweave.core.VeriCoreError" = "import com.trustweave.core.TrustWeaveError"
    "import com.trustweave.core.VeriCoreConstants" = "import com.trustweave.core.TrustWeaveConstants"
    "import com.trustweave.testkit.VeriCoreTestFixture" = "import com.trustweave.testkit.TrustWeaveTestFixture"
    
    # Extension functions
    "toVeriCoreError" = "toTrustWeaveError"
    
    # Variable names in code examples (optional - be careful)
    "val vericore = VeriCore" = "val trustweave = TrustWeave"
    "vericore: VeriCore" = "trustweave: TrustWeave"
    "vericore." = "trustweave."
    
    # Documentation references
    "VeriCore library" = "TrustWeave library"
    "VeriCore provides" = "TrustWeave provides"
    "VeriCore instance" = "TrustWeave instance"
    "VeriCore operations" = "TrustWeave operations"
    "VeriCore API" = "TrustWeave API"
}

$totalFiles = 0
$totalReplacements = 0

foreach ($file in $files) {
    $content = Get-Content $file.FullName -Raw -ErrorAction SilentlyContinue
    if ($null -eq $content) { continue }
    
    $originalContent = $content
    $fileReplacements = 0
    
    foreach ($pattern in $replacements.Keys) {
        $newPattern = $replacements[$pattern]
        if ($content -match [regex]::Escape($pattern)) {
            $content = $content -replace [regex]::Escape($pattern), $newPattern
            $fileReplacements++
        }
    }
    
    if ($content -ne $originalContent) {
        Set-Content -Path $file.FullName -Value $content -NoNewline
        $totalFiles++
        $totalReplacements += $fileReplacements
        Write-Host "Updated: $($file.FullName)" -ForegroundColor Green
    }
}

Write-Host ""
Write-Host "=== COMPLETE ===" -ForegroundColor Blue
Write-Host "Files updated: $totalFiles" -ForegroundColor Cyan
Write-Host "Total replacements: $totalReplacements" -ForegroundColor Cyan

