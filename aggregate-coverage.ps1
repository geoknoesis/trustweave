# Script to aggregate Kover coverage reports from all modules

$modules = @(
    "core:trustweave-core",
    "chains:trustweave-anchor", 
    "did:trustweave-did",
    "kms:trustweave-kms",
    "core:trustweave-json",
    "core:trustweave-testkit",
    "distribution:trustweave-examples",
    "chains:plugins:ganache",
    "kms:plugins:waltid",
    "chains:plugins:algorand",
    "chains:plugins:polygon",
    "did:plugins:godiddy",
    "chains:plugins:indy"
)

$totalClasses = 0
$coveredClasses = 0
$totalMethods = 0
$coveredMethods = 0
$totalBranches = 0
$coveredBranches = 0
$totalLines = 0
$coveredLines = 0
$totalInstructions = 0
$coveredInstructions = 0

$moduleResults = @()

foreach ($module in $modules) {
    $modulePath = $module -replace ":", "\"
    $xmlPath = Join-Path $modulePath "build\reports\kover\report.xml"
    if (Test-Path $xmlPath) {
        [xml]$xml = Get-Content $xmlPath
        
        $classMissed = 0
        $classCovered = 0
        $methodMissed = 0
        $methodCovered = 0
        $branchMissed = 0
        $branchCovered = 0
        $lineMissed = 0
        $lineCovered = 0
        $instructionMissed = 0
        $instructionCovered = 0
        
        # Parse counters from XML
        $counters = $xml.report.counter
        foreach ($counter in $counters) {
            $type = $counter.type
            $missed = [int]$counter.missed
            $covered = [int]$counter.covered
            
            switch ($type) {
                "CLASS" { 
                    $classMissed += $missed
                    $classCovered += $covered
                }
                "METHOD" { 
                    $methodMissed += $missed
                    $methodCovered += $covered
                }
                "BRANCH" { 
                    $branchMissed += $missed
                    $branchCovered += $covered
                }
                "LINE" { 
                    $lineMissed += $missed
                    $lineCovered += $covered
                }
                "INSTRUCTION" { 
                    $instructionMissed += $missed
                    $instructionCovered += $covered
                }
            }
        }
        
        $totalClasses += ($classMissed + $classCovered)
        $coveredClasses += $classCovered
        $totalMethods += ($methodMissed + $methodCovered)
        $coveredMethods += $methodCovered
        $totalBranches += ($branchMissed + $branchCovered)
        $coveredBranches += $branchCovered
        $totalLines += ($lineMissed + $lineCovered)
        $coveredLines += $lineCovered
        $totalInstructions += ($instructionMissed + $instructionCovered)
        $coveredInstructions += $instructionCovered
        
        $classTotal = $classMissed + $classCovered
        $methodTotal = $methodMissed + $methodCovered
        $branchTotal = $branchMissed + $branchCovered
        $lineTotal = $lineMissed + $lineCovered
        $instructionTotal = $instructionMissed + $instructionCovered
        
        $classPct = if ($classTotal -gt 0) { [math]::Round(($classCovered / $classTotal) * 100, 1) } else { 0 }
        $methodPct = if ($methodTotal -gt 0) { [math]::Round(($methodCovered / $methodTotal) * 100, 1) } else { 0 }
        $branchPct = if ($branchTotal -gt 0) { [math]::Round(($branchCovered / $branchTotal) * 100, 1) } else { 0 }
        $linePct = if ($lineTotal -gt 0) { [math]::Round(($lineCovered / $lineTotal) * 100, 1) } else { 0 }
        $instructionPct = if ($instructionTotal -gt 0) { [math]::Round(($instructionCovered / $instructionTotal) * 100, 1) } else { 0 }
        
        $moduleResults += [PSCustomObject]@{
            Module = $module
            Classes = "$classPct% ($classCovered/$classTotal)"
            Methods = "$methodPct% ($methodCovered/$methodTotal)"
            Branches = "$branchPct% ($branchCovered/$branchTotal)"
            Lines = "$linePct% ($lineCovered/$lineTotal)"
            Instructions = "$instructionPct% ($instructionCovered/$instructionTotal)"
        }
    }
}

# Calculate overall percentages
$overallClassPct = if ($totalClasses -gt 0) { [math]::Round(($coveredClasses / $totalClasses) * 100, 1) } else { 0 }
$overallMethodPct = if ($totalMethods -gt 0) { [math]::Round(($coveredMethods / $totalMethods) * 100, 1) } else { 0 }
$overallBranchPct = if ($totalBranches -gt 0) { [math]::Round(($coveredBranches / $totalBranches) * 100, 1) } else { 0 }
$overallLinePct = if ($totalLines -gt 0) { [math]::Round(($coveredLines / $totalLines) * 100, 1) } else { 0 }
$overallInstructionPct = if ($totalInstructions -gt 0) { [math]::Round(($coveredInstructions / $totalInstructions) * 100, 1) } else { 0 }

Write-Host "`n=== TRUSTWEAVE PROJECT COVERAGE SUMMARY ===" -ForegroundColor Cyan
Write-Host "`nOverall Coverage:" -ForegroundColor Yellow
Write-Host "  Class Coverage:      $overallClassPct% ($coveredClasses/$totalClasses)" -ForegroundColor $(if ($overallClassPct -ge 80) { "Green" } elseif ($overallClassPct -ge 50) { "Yellow" } else { "Red" })
Write-Host "  Method Coverage:     $overallMethodPct% ($coveredMethods/$totalMethods)" -ForegroundColor $(if ($overallMethodPct -ge 80) { "Green" } elseif ($overallMethodPct -ge 50) { "Yellow" } else { "Red" })
Write-Host "  Branch Coverage:     $overallBranchPct% ($coveredBranches/$totalBranches)" -ForegroundColor $(if ($overallBranchPct -ge 80) { "Green" } elseif ($overallBranchPct -ge 50) { "Yellow" } else { "Red" })
Write-Host "  Line Coverage:       $overallLinePct% ($coveredLines/$totalLines)" -ForegroundColor $(if ($overallLinePct -ge 80) { "Green" } elseif ($overallLinePct -ge 50) { "Yellow" } else { "Red" })
Write-Host "  Instruction Coverage: $overallInstructionPct% ($coveredInstructions/$totalInstructions)" -ForegroundColor $(if ($overallInstructionPct -ge 80) { "Green" } elseif ($overallInstructionPct -ge 50) { "Yellow" } else { "Red" })

Write-Host "`nCoverage by Module:" -ForegroundColor Yellow
$moduleResults | Format-Table -AutoSize

# Export to JSON for further processing
$summary = @{
    Overall = @{
        Classes = @{ Coverage = $overallClassPct; Covered = $coveredClasses; Total = $totalClasses }
        Methods = @{ Coverage = $overallMethodPct; Covered = $coveredMethods; Total = $totalMethods }
        Branches = @{ Coverage = $overallBranchPct; Covered = $coveredBranches; Total = $totalBranches }
        Lines = @{ Coverage = $overallLinePct; Covered = $coveredLines; Total = $totalLines }
        Instructions = @{ Coverage = $overallInstructionPct; Covered = $coveredInstructions; Total = $totalInstructions }
    }
    Modules = $moduleResults
}

$summary | ConvertTo-Json -Depth 10 | Out-File "coverage-summary.json" -Encoding UTF8
Write-Host "`nDetailed summary saved to: coverage-summary.json" -ForegroundColor Green

