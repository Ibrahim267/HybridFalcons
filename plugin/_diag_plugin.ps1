$ErrorActionPreference = 'Continue'
$cfg = "$env:APPDATA\Google\AndroidStudio2024.3.2"
$log = "$env:LOCALAPPDATA\Google\AndroidStudio2024.3.2\log\idea.log"
Write-Output '--- studio64 process ---'
Get-Process studio64 -ErrorAction SilentlyContinue | ForEach-Object { '  studio64 PID ' + $_.Id + ' started ' + $_.StartTime }
Write-Output '--- all fitdeveloper plugin-load lines (last 12) ---'
if (Test-Path $log) {
  $l = Get-Content $log
  Write-Output ('log size: ' + (Get-Item $log).Length + 'B, lines: ' + $l.Count)
  $l | Select-String -SimpleMatch 'com.fitdeveloper.plugin' | Select-Object -Last 12 | ForEach-Object { $_.Line }
  Write-Output '--- fitdeveloper runtime markers (last 8) ---'
  $l | Select-String -SimpleMatch '[FitDeveloper]' | Select-Object -Last 8 | ForEach-Object { $_.Line }
  Write-Output '--- plugin errors (last 12) ---'
  $l | Select-String -Pattern 'plugin.*error|invalid plugin descriptor|disabled' -CaseSensitive:$false | Select-Object -Last 12 | ForEach-Object { $_.Line }
  Write-Output '--- log last 5 lines ---'
  $l | Select-Object -Last 5 | ForEach-Object { $_.Line }
} else {
  Write-Output ('LOG MISSING: ' + $log)
}
Write-Output '--- disabled_plugins.txt ---'
$dp = "$cfg\disabled_plugins.txt"
if (Test-Path $dp) { Write-Output 'EXISTS:'; Get-Content $dp } else { Write-Output 'not present' }
Write-Output '--- seeded plugin dirs ---'
Get-ChildItem "$cfg\plugins" -Directory -ErrorAction SilentlyContinue | ForEach-Object { '  ' + $_.Name }
Write-Output '--- fitdeveloper jar check (any version) ---'
Get-ChildItem "$cfg\plugins\fitdeveloper-plugin\lib" -ErrorAction SilentlyContinue | ForEach-Object { '  ' + $_.Name + '  ' + $_.Length + 'B' }
Write-Output '--- port 8790-8795 listeners ---'
netstat -ano | Select-String ':879'
