# Final cleanup script for remaining VeriCore references
Write-Host "=== FINAL VeriCore CLEANUP ===" -ForegroundColor Blue

$files = Get-ChildItem -Path . -Recurse -Include *.kt,*.kts,*.md -Exclude *build*,*.gradle*,*.idea* | 
    Where-Object { 
        $_.FullName -notmatch "\\build\\" -and 
        $_.FullName -notmatch "\\.gradle\\" -and 
        $_.FullName -notmatch "rename-.*\.ps1" -and
        $_.FullName -notmatch "update-.*\.ps1" -and
        $_.FullName -notmatch "final-.*\.ps1"
    }

$totalFiles = 0
$totalReplacements = 0

foreach ($file in $files) {
    $content = Get-Content -Path $file.FullName -Raw -ErrorAction SilentlyContinue
    if ($null -eq $content) { continue }
    
    $originalContent = $content
    
    # Replace VeriCore with TrustWeave (word boundary)
    $content = $content -replace '\bVeriCore\b', 'TrustWeave'
    
    if ($content -ne $originalContent) {
        Set-Content -Path $file.FullName -Value $content -NoNewline
        $matches = ([regex]::Matches($originalContent, '\bVeriCore\b')).Count
        $totalFiles++
        $totalReplacements += $matches
        Write-Host "Updated: $($file.FullName) ($matches replacements)" -ForegroundColor Green
    }
}

Write-Host "`n=== CLEANUP COMPLETE ===" -ForegroundColor Green
Write-Host "Files updated: $totalFiles" -ForegroundColor Cyan
Write-Host "Total replacements: $totalReplacements" -ForegroundColor Cyan

