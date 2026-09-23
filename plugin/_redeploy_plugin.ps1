$ErrorActionPreference = 'Continue'
$repo = 'C:\Users\loq\Documents\hackathons\JetBrains\CrunchGuard\plugin'
$asCfg = "$env:APPDATA\Google\AndroidStudio2024.3.2\plugins"
$z = "$repo\build\distributions\fitdeveloper-plugin-2.0.0.zip"
if (-not (Test-Path $z)) { Write-Output 'ZIP MISSING'; exit 1 }

# 0. verify the new engine class is inside
$t = "$env:TEMP\cgcheck"
Remove-Item $t -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Path $t | Out-Null
tar -xf $z -C $t
$j = (Get-ChildItem $t -Recurse -Filter *.jar)[0].FullName
$entries = tar -tf $j
$hasEngine = ($entries | Select-String 'FitDeveloperEngine.class') -ne $null
Write-Output ("jar engine class present: " + $hasEngine)
if (-not $hasEngine) { Write-Output 'ABORT - stale zip'; exit 1 }

# 1. close Android Studio
$as = Get-Process studio64 -ErrorAction SilentlyContinue
if ($as) {
  Stop-Process -Name studio64 -Force
  Start-Sleep -Seconds 4
  Write-Output 'AS killed'
} else {
  Write-Output 'AS was not running'
}

# 2. remove old seeded plugin
Remove-Item "$asCfg\fitdeveloper-plugin" -Recurse -Force -ErrorAction SilentlyContinue
if (Test-Path "$asCfg\fitdeveloper-plugin") { Write-Output 'OLD SEED STILL THERE' } else { Write-Output 'old seed removed' }

# 3. seed the new build
New-Item -ItemType Directory -Path "$asCfg\fitdeveloper-plugin" -Force | Out-Null
tar -xf $z -C "$asCfg\fitdeveloper-plugin"
Write-Output 'seeded files:'
Get-ChildItem "$asCfg\fitdeveloper-plugin" -Recurse -File | ForEach-Object { '  ' + $_.FullName.Substring($asCfg.Length) + '  ' + $_.Length }

# 4. refresh desktop zip
Copy-Item $z "$env:USERPROFILE\Desktop\fitdeveloper-plugin-2.0.0.zip" -Force
Write-Output 'desktop zip refreshed'

# 5. relaunch AS with the test project (returns immediately)
Start-Process 'C:\Program Files\Android\Android Studio\bin\studio64.exe' -ArgumentList '"C:\Users\loq\Documents\hackathons\JetBrains\CrunchGuard-IDE-test"'
Write-Output 'AS relaunching with CrunchGuard-IDE-test'
