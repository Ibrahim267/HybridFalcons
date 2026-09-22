$ErrorActionPreference = 'Continue'
$cfg = "$env:APPDATA\Google\AndroidStudio2024.3.2\plugins"
Stop-Process -Name studio64 -Force -ErrorAction SilentlyContinue
Start-Sleep -Seconds 3
Remove-Item "$cfg\crunchguard-plugin" -Recurse -Force -ErrorAction SilentlyContinue
if (Test-Path "$cfg\crunchguard-plugin") { Write-Output 'REMOVE FAILED'; exit 1 }
Write-Output 'old nested seed removed'
tar -xf 'C:\Users\loq\Documents\hackathons\JetBrains\CrunchGuard\plugin\build\distributions\crunchguard-plugin-2.2.0.zip' -C $cfg
Write-Output 'seed layout now:'
Get-ChildItem "$cfg\crunchguard-plugin" -Recurse -File | ForEach-Object { '  ' + $_.FullName.Substring($cfg.Length) + '  ' + $_.Length }
Start-Process 'C:\Program Files\Android\Android Studio\bin\studio64.exe' -ArgumentList '"C:\Users\loq\Documents\hackathons\JetBrains\CrunchGuard-IDE-test"'
Write-Output 'AS relaunching (correct seed)'
