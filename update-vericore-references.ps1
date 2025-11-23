# PowerShell script to update all remaining VeriCore references to TrustWeave
# This updates imports, function names, comments, and documentation

Write-Host "=== UPDATING VeriCore REFERENCES TO TrustWeave ===" -ForegroundColor Blue

$replacements = @{
    # Import statements
    "import com.trustweave.VeriCore" = "import com.trustweave.TrustWeave"
    "import com.trustweave.core.VeriCoreException" = "import com.trustweave.core.TrustWeaveException"
    "import com.trustweave.core.VeriCoreError" = "import com.trustweave.core.TrustWeaveError"
    "import com.trustweave.core.VeriCoreConstants" = "import com.trustweave.core.TrustWeaveConstants"
    "import com.trustweave.VeriCoreDefaults" = "import com.trustweave.TrustWeaveDefaults"
    "import com.trustweave.testkit.VeriCoreTestFixture" = "import com.trustweave.testkit.TrustWeaveTestFixture"
    
    # Function names
    "toVeriCoreError" = "toTrustWeaveError"
    "VeriCoreError" = "TrustWeaveError"
    "VeriCoreException" = "TrustWeaveException"
    "VeriCoreConstants" = "TrustWeaveConstants"
    "VeriCoreDefaults" = "TrustWeaveDefaults"
    "VeriCoreTestFixture" = "TrustWeaveTestFixture"
    
    # Class references in code
    "VeriCore.create" = "TrustWeave.create"
    "VeriCore\." = "TrustWeave."
    "VeriCore\s" = "TrustWeave "
    "VeriCore\)" = "TrustWeave)"
    "VeriCore\(" = "TrustWeave("
    
    # Comments and documentation
    "VeriCore library" = "TrustWeave library"
    "VeriCore provides" = "TrustWeave provides"
    "VeriCore instance" = "TrustWeave instance"
    "VeriCore operations" = "TrustWeave operations"
    "VeriCore API" = "TrustWeave API"
    "VeriCore modules" = "TrustWeave modules"
    "VeriCore tests" = "TrustWeave tests"
    "VeriCore facade" = "TrustWeave facade"
    "VeriCore standardises" = "TrustWeave standardises"
    "VeriCore standardizes" = "TrustWeave standardizes"
    "VeriCore supports" = "TrustWeave supports"
    "VeriCore manages" = "TrustWeave manages"
    "VeriCore enables" = "TrustWeave enables"
    "VeriCore uses" = "TrustWeave uses"
    "VeriCore can" = "TrustWeave can"
    "VeriCore will" = "TrustWeave will"
    "VeriCore's" = "TrustWeave's"
    "VeriCore\"" = "TrustWeave\""
    "VeriCore\`" = "TrustWeave`"
    
    # Variable names in examples (be careful with these)
    "val vericore = " = "val trustweave = "
    "val vericore:" = "val trustweave:"
    "vericore\." = "trustweave."
    "vericore\s" = "trustweave "
}

$filesToProcess = Get-ChildItem -Path . -Recurse -Include *.kt,*.md,*.kts -Exclude *build*,*\.gradle*,*\.idea* | 
    Where-Object { $_.FullName -notmatch "\\build\\" -and $_.FullName -notmatch "\\.gradle\\" }

$totalFiles = 0
$totalReplacements = 0

foreach ($file in $filesToProcess) {
    $content = Get-Content -Path $file.FullName -Raw -ErrorAction SilentlyContinue
    if ($null -eq $content) { continue }
    
    $originalContent = $content
    $fileReplacements = 0
    
    foreach ($pattern in $replacements.Keys) {
        $replacement = $replacements[$pattern]
        if ($content -match $pattern) {
            $content = $content -replace $pattern, $replacement
            $fileReplacements++
        }
    }
    
    if ($content -ne $originalContent) {
        Set-Content -Path $file.FullName -Value $content -NoNewline
        $totalFiles++
        $totalReplacements += $fileReplacements
        Write-Host "Updated: $($file.FullName) ($fileReplacements replacements)" -ForegroundColor Green
    }
}

Write-Host "`n=== UPDATE COMPLETE ===" -ForegroundColor Green
Write-Host "Files updated: $totalFiles" -ForegroundColor Cyan
Write-Host "Total replacements: $totalReplacements" -ForegroundColor Cyan

