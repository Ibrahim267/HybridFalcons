$ErrorActionPreference = 'Continue'
$log = "$env:LOCALAPPDATA\Google\AndroidStudio2024.3.2\log\idea.log"
$l = Get-Content $log -ErrorAction SilentlyContinue
Write-Output '--- safe mode / abnormal shutdown lines ---'
$l | Select-String -Pattern 'safe mode|Safe mode|SAFE_MODE|abnormal|third.party plugins are disabled' | Select-Object -Last 6 | ForEach-Object { $_.Line }
Write-Output '--- boot plugin count lines (last 8) ---'
$l | Select-String -Pattern 'loaded [0-9]+ plugins|plugins in .+ seconds|StartupActivity' | Select-Object -Last 8 | ForEach-Object { $_.Line }
Write-Output '--- last 3 lines of log ---'
Get-Content $log -Tail 3
