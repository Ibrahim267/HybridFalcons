$ErrorActionPreference = 'SilentlyContinue'
Write-Output '=== ANDROID STUDIO (last FitDeveloper lines) ==='
Select-String -Path "$env:LOCALAPPDATA\Google\AndroidStudio2024.3.2\log\idea.log" -Pattern 'FitDeveloper' -SimpleMatch |
  Select-Object -Last 5 | ForEach-Object { $_.Line.Substring(0, [Math]::Min(170, $_.Line.Length)) }

Write-Output '=== INTELLIJ (last FitDeveloper lines) ==='
Select-String -Path "$env:LOCALAPPDATA\JetBrains\IntelliJIdea2026.2\log\idea.log" -Pattern 'FitDeveloper' -SimpleMatch |
  Select-Object -Last 6 | ForEach-Object { $_.Line.Substring(0, [Math]::Min(170, $_.Line.Length)) }

Write-Output '=== INTELLIJ severity check (SEVERE/Cannot init/ClassNotFound near plugin) ==='
$bad = Select-String -Path "$env:LOCALAPPDATA\JetBrains\IntelliJIdea2026.2\log\idea.log" -Pattern 'FitDeveloper|com.fitdeveloper' -SimpleMatch:$false |
  Where-Object { $_.Line -match 'SEVERE|Cannot init|ClassNotFound' } |
  Select-Object -Last 3
if ($bad) { $bad | ForEach-Object { $_.Line.Substring(0, [Math]::Min(170, $_.Line.Length)) } }
else { Write-Output 'CLEAN - no toolwindow errors' }

Write-Output '=== IDE processes ==='
Get-Process idea64, studio64 -ErrorAction SilentlyContinue | ForEach-Object { Write-Output ($_.Name + ' pid=' + $_.Id) }

Write-Output '=== relay ports listening (8790..8795) ==='
Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue | Where-Object { $_.LocalPort -ge 8790 -and $_.LocalPort -le 8795 } |
  Select-Object -Unique LocalPort | Sort-Object LocalPort | ForEach-Object { Write-Output ('port ' + $_.LocalPort) }
