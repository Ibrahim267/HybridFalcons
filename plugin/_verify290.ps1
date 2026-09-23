# ============================================================
#  FitDeveloper 2.9.0 verify - dashboards clean + version check
# ============================================================
$ErrorActionPreference = 'Continue'

function Get-Page($url) {
  try { return (Invoke-WebRequest -UseBasicParsing $url).Content } catch { return "" }
}

$d0 = Get-Page 'http://localhost:8790/'
$d2 = Get-Page 'http://localhost:8792/'
Write-Output ("AS relay page bytes: " + $d0.Length)
Write-Output ("IC relay page bytes: " + $d2.Length)
Write-Output ("AS demo hits: "        + ([regex]::Matches($d0, 'demo')).Count +
             " | trigger-btn hits: " + ([regex]::Matches($d0, 'breakNowBtn')).Count +
             " | instant-btn hits: " + ([regex]::Matches($d0, 'instantBtn')).Count +
             " | stats-row hits: "   + ([regex]::Matches($d0, 'stats-row')).Count +
             " | v2.9.0 hits: "      + ([regex]::Matches($d0, '2\.9\.0')).Count)
Write-Output ("IC demo hits: "        + ([regex]::Matches($d2, 'demo')).Count +
             " | trigger-btn hits: " + ([regex]::Matches($d2, 'breakNowBtn')).Count +
             " | v2.9.0 hits: "      + ([regex]::Matches($d2, '2\.9\.0')).Count)

$as = Get-ChildItem "$env:LOCALAPPDATA\Google\AndroidStudio2024.3.2\log\idea*.log" |
      Sort-Object LastWriteTime | Select-Object -Last 1
Write-Output "=== AS log (latest: $($as.Name)) ==="
Select-String -Path $as.FullName -Pattern 'custom plugins' | Select-Object -First 2 | ForEach-Object { $_.Line }
Select-String -Path $as.FullName -Pattern '\[FitDeveloper\] (relay|engine|coding)' | Select-Object -Last 4 | ForEach-Object { $_.Line }

$iu = Get-ChildItem "$env:LOCALAPPDATA\JetBrains\IntelliJIdea2026.2\log\idea*.log" |
      Sort-Object LastWriteTime | Select-Object -Last 1
Write-Output "=== IC log (latest: $($iu.Name)) ==="
Select-String -Path $iu.FullName -Pattern 'custom plugins' | Select-Object -First 2 | ForEach-Object { $_.Line }
Select-String -Path $iu.FullName -Pattern '\[FitDeveloper\] (relay|engine|coding)' | Select-Object -Last 4 | ForEach-Object { $_.Line }

Write-Output "=== relay /api/health version ==="
try { Write-Output (Invoke-WebRequest -UseBasicParsing 'http://localhost:8790/api/health').Content } catch { Write-Output "AS health FAILED" }
try { Write-Output (Invoke-WebRequest -UseBasicParsing 'http://localhost:8792/api/health').Content } catch { Write-Output "IC health FAILED" }
Write-Output "=== verify done ==="
