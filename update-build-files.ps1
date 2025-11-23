# Script to update build.gradle.kts files with new module paths

Write-Host "Updating build.gradle.kts files..." -ForegroundColor Green

# Find all build.gradle.kts files
$buildFiles = Get-ChildItem -Path . -Filter "build.gradle.kts" -Recurse | Where-Object {
    $_.FullName -notmatch "\\build\\" -and
    $_.FullName -notmatch "\\node_modules\\" -and
    $_.FullName -notmatch "\\\.gradle\\"
}

$updatedCount = 0

foreach ($file in $buildFiles) {
    $content = Get-Content $file.FullName -Raw
    $originalContent = $content
    $modified = $false
    
    # Update :core to :credentials:core
    if ($content -match 'project\(":core"\)') {
        $content = $content -replace 'project\(":core"\)', 'project(":credentials:core")'
        $modified = $true
    }
    if ($content -match '":core"') {
        $content = $content -replace '":core"', '":credentials:core"'
        $modified = $true
    }
    
    # Update wallet plugin paths
    if ($content -match '":credentials:plugins:wallet:') {
        $content = $content -replace '":credentials:plugins:wallet:database"', '":wallet:plugins:database"'
        $content = $content -replace '":credentials:plugins:wallet:file"', '":wallet:plugins:file"'
        $content = $content -replace '":credentials:plugins:wallet:cloud"', '":wallet:plugins:cloud"'
        $modified = $true
    }
    
    if ($modified) {
        Set-Content -Path $file.FullName -Value $content -NoNewline
        $updatedCount++
        $relativePath = $file.FullName.Replace((Get-Location).Path + "\", "")
        Write-Host "  Updated: $relativePath" -ForegroundColor Yellow
    }
}

Write-Host ""
Write-Host "Updated $updatedCount build.gradle.kts files" -ForegroundColor Green
