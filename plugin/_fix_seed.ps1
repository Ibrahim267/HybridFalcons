param([switch]$NoLaunch)
$ErrorActionPreference = 'Continue'
$cfg = "$env:APPDATA\Google\AndroidStudio2024.3.2\plugins"
Stop-Process -Name studio64 -Force -ErrorAction SilentlyContinue
Start-Sleep -Seconds 3
Get-ChildItem $cfg -Directory -ErrorAction SilentlyContinue |
  Where-Object { $_.Name -match '^(crunchguard|fitdeveloper)-plugin' } |
  ForEach-Object { Remove-Item $_.FullName -Recurse -Force -ErrorAction SilentlyContinue; Write-Output ('removed old: ' + $_.Name) }
$zip = 'C:\Users\loq\Documents\hackathons\JetBrains\CrunchGuard\plugin\build\distributions\fitdeveloper-plugin-2.4.1.zip'
tar -xf $zip -C $cfg
Write-Output 'seed layout now:'
Get-ChildItem $cfg -Directory | ForEach-Object { '  ' + $_.Name }
if (-not $NoLaunch) {
  Start-Process 'C:\Program Files\Android\Android Studio\bin\studio64.exe' -ArgumentList '"C:\Users\loq\Documents\hackathons\JetBrains\CrunchGuard-IDE-test"'
  Write-Output 'AS relaunching (FitDeveloper 2.4.1)'
}
