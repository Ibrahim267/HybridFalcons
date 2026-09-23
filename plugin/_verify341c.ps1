$ErrorActionPreference = 'Continue'
Write-Output '=== https ports via curl.exe -k ==='
foreach ($p in 8791,8793) {
  $out = & curl.exe -sk --max-time 6 ('https://localhost:' + $p + '/api/health') 2>&1
  Write-Output ($p.ToString() + ' -> ' + ($out -join ' '))
}
Write-Output '=== processes ==='
Get-Process studio64,idea64 -ErrorAction SilentlyContinue | ForEach-Object { Write-Output ($_.Name + ' PID=' + $_.Id + ' up=' + $_.StartTime) }
Write-Output '=== IC log tail ==='
$icLog = "$env:LOCALAPPDATA\JetBrains\IntelliJIdea2026.2\log\idea.log"
if (Test-Path $icLog) { Get-Content $icLog -Tail 6 } else { Write-Output 'no log file' }
