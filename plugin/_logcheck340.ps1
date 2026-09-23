$ErrorActionPreference = 'Continue'
$asLog = "$env:LOCALAPPDATA\Google\AndroidStudio2024.3.2\log\idea.log"
$icLog = "$env:LOCALAPPDATA\JetBrains\IntelliJIdea2026.2\log\idea.log"
Write-Output "=== AS log: $asLog ==="
if (Test-Path $asLog) {
  Get-Content $asLog -Tail 800 | Select-String -Pattern 'FitDeveloper','Loaded custom plugins','relay on','https on' | Select-Object -Last 14
} else { Write-Output 'AS log not found' }
Write-Output "=== IC log: $icLog ==="
if (Test-Path $icLog) {
  Get-Content $icLog -Tail 800 | Select-String -Pattern 'FitDeveloper','Loaded custom plugins','relay on','https on' | Select-Object -Last 14
} else { Write-Output 'IC log not found' }
Write-Output "=== IDE processes ==="
Get-Process studio64,idea64 -ErrorAction SilentlyContinue | Select-Object Name,Id,StartTime
