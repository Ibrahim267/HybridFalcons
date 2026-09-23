$ErrorActionPreference = 'Continue'
Write-Output '=== https ports (TLS, skip cert) ==='
foreach ($p in 8791,8793) {
  try {
    $r = Invoke-RestMethod -Uri ('https://localhost:' + $p + '/api/health') -TimeoutSec 5 -SkipCertificateCheck
    Write-Output ($p.ToString() + ' -> version=' + $r.version)
  } catch { Write-Output ($p.ToString() + ' -> ' + $_.Exception.Message) }
}
Write-Output '=== IC log recheck ==='
$icLog = "$env:LOCALAPPDATA\JetBrains\IntelliJIdea2026.2\log\idea.log"
$line = Select-String -Path $icLog -Pattern 'Loaded custom plugins: FitDeveloper' | Select-Object -Last 1
Write-Output $(if ($line) { $line.Line } else { 'no match yet' })
$asLog = "$env:LOCALAPPDATA\Google\AndroidStudio2024.3.2\log\idea.log"
$line2 = Select-String -Path $asLog -Pattern 'Loaded custom plugins: FitDeveloper' | Select-Object -Last 1
Write-Output ('AS: ' + $(if ($line2) { $line2.Line } else { 'no match yet' }))
