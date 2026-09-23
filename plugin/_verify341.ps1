# 3.4.1 verification: 4 relay ports, walk footer, IDE plugin logs
$ErrorActionPreference = 'Continue'
Write-Output '=== ports ==='
foreach ($p in 8790,8791,8792,8793) {
  try {
    $r = Invoke-RestMethod -Uri ('http://localhost:' + $p + '/api/health') -TimeoutSec 4
    Write-Output ($p.ToString() + ' -> version=' + $r.version + ' service=' + $r.service)
  } catch {
    Write-Output ($p.ToString() + ' -> DOWN')
  }
}
Write-Output '=== walk footer ==='
try {
  $walk = Invoke-WebRequest -Uri 'http://localhost:8790/walk' -TimeoutSec 5 -UseBasicParsing
  $m = [regex]::Match($walk.Content, 'FitDeveloper plugin v[0-9.]+')
  Write-Output ('served walk footer: ' + $m.Value)
  $hasBar = $walk.Content -match 'barText'
  Write-Output ('walk.html mentions barText (should be False): ' + $hasBar)
} catch { Write-Output ('walk fetch failed: ' + $_.Exception.Message) }
Write-Output '=== ide logs ==='
$asLog = "$env:LOCALAPPDATA\Google\AndroidStudio2024.3.2\log\idea.log"
$icLog = "$env:LOCALAPPDATA\JetBrains\IntelliJIdea2026.2\log\idea.log"
foreach ($log in @($asLog, $icLog)) {
  if (Test-Path $log) {
    $line = Select-String -Path $log -Pattern 'Loaded custom plugins: FitDeveloper' | Select-Object -Last 1
    Write-Output ((Split-Path (Split-Path $log) -Leaf) + ': ' + $(if ($line) { $line.Line } else { 'no match yet' }))
  } else { Write-Output ($log + ' missing') }
}
Write-Output '=== ide-activity ==='
try {
  $act = Invoke-RestMethod -Uri 'http://localhost:8790/api/ide-activity' -TimeoutSec 5
  Write-Output ('breakScreen=' + $act.breakScreen + ' targetSteps=' + $act.targetSteps + ' rampMinutes=' + $act.rampMinutes)
} catch { Write-Output ('ide-activity failed: ' + $_.Exception.Message) }
Write-Output '=== verify done ==='
