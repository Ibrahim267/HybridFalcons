$ErrorActionPreference = 'Continue'
$icLog = "$env:LOCALAPPDATA\JetBrains\IntelliJIdea2026.2\log\idea.log"
Write-Output "=== IC tail (unfiltered, last 25) ==="
if (Test-Path $icLog) { Get-Content $icLog -Tail 25 } else { Write-Output 'IC log not found' }
$asLog = "$env:LOCALAPPDATA\Google\AndroidStudio2024.3.2\log\idea.log"
Write-Output "=== AS log info ==="
Get-Item $asLog | Select-Object FullName,Length,LastWriteTime
Write-Output "=== AS FitDeveloper lines (whole file) ==="
Get-Content $asLog | Select-String -Pattern 'FitDeveloper' | Select-Object -Last 10
