<#
.SYNOPSIS
  Lightweight regression check for TrustWeave documentation Kotlin patterns.

.DESCRIPTION
  Requires ripgrep (rg) on PATH. Fails if disallowed patterns appear outside the allowlist.
  Rules: docs/contributing/code-example-style-guide.md — "Canonical Kotlin snippet contract".

.EXAMPLE
  pwsh -File scripts/check-doc-snippets.ps1
#>
param(
    [string]$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
)

$ErrorActionPreference = "Stop"
$docsDir = Join-Path $RepoRoot "docs"
if (-not (Test-Path $docsDir)) {
    Write-Error "docs directory not found: $docsDir"
    exit 1
}

if (-not (Get-Command rg -ErrorAction SilentlyContinue)) {
    Write-Warning "ripgrep (rg) not on PATH; skipping check-doc-snippets (install: https://github.com/BurntSushi/ripgrep)"
    exit 0
}

$failed = $false
function Invoke-DocCheck {
    param([string]$Label, [string[]]$RgArgs)
    $output = & rg @RgArgs $docsDir 2>$null
    $code = $LASTEXITCODE
    if ($code -eq 0 -and $output) {
        Write-Host "FAILED: $Label" -ForegroundColor Red
        $output | ForEach-Object { Write-Host $_ }
        $script:failed = $true
    }
    else {
        Write-Host "OK: $Label" -ForegroundColor Green
    }
}

# Legacy VM key id extraction (use extractKeyId() instead)
Invoke-DocCheck "No verificationMethod...substringAfter(`"#`")" @(
    '-n', '--glob', '*.md',
    '-g', '!**/code-example-style-guide.md',
    'verificationMethod\..*substringAfter\("#"\)'
)

# Non-existent API
Invoke-DocCheck "No IssuerIdentity.from" @(
    '-n', '--glob', '*.md',
    'IssuerIdentity\.from'
)

# Removed testkit helpers (use getOrThrowDid / credential.results.getOrThrow / trust.types.getOrThrow)
Invoke-DocCheck "No org.trustweave.testkit.getOrFail" @(
    '-n', '--glob', '*.md',
    'org\.trustweave\.testkit\.getOrFail'
)

Invoke-DocCheck "No org.trustweave.trust.types.getOrFail" @(
    '-n', '--glob', '*.md',
    'org\.trustweave\.trust\.types\.getOrFail'
)

if ($failed) {
    Write-Host "`nSee docs/contributing/code-example-style-guide.md" -ForegroundColor Yellow
    exit 1
}
exit 0
