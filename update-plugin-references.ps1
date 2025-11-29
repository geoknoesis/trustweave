# Update plugin references from vericore.shared to trustweave.shared

param(
    [switch]$DryRun = $false
)

Write-Host "=== UPDATING PLUGIN REFERENCES ===" -ForegroundColor Blue
Write-Host ""

if ($DryRun) {
    Write-Host "DRY RUN MODE - No files will be modified" -ForegroundColor Yellow
    Write-Host ""
}

$count = 0
Get-ChildItem -Recurse -Include "build.gradle.kts" | Where-Object {
    $_.FullName -notlike "*\build\*" -and
    $_.FullName -notlike "*\.gradle\*"
} | ForEach-Object {
    $content = Get-Content $_.FullName -Raw -ErrorAction SilentlyContinue
    if ($null -eq $content) { return }

    if ($content -match 'vericore\.shared') {
        $count++
        if ($DryRun) {
            Write-Host "  [DRY RUN] Would update: $($_.FullName)" -ForegroundColor Cyan
        } else {
            $content = $content -replace 'vericore\.shared', 'trustweave.shared'
            Set-Content -Path $_.FullName -Value $content -NoNewline
            Write-Host "  Updated: $($_.Name)" -ForegroundColor Green
        }
    }
}

Write-Host ""
Write-Host "Summary: $count files would be updated" -ForegroundColor Green

if ($DryRun) {
    Write-Host ""
    Write-Host "Run without -DryRun to apply changes" -ForegroundColor Cyan
} else {
    Write-Host ""
    Write-Host "UPDATE COMPLETED!" -ForegroundColor Blue
}

Write-Host ""
Write-Host "Done!" -ForegroundColor Blue

