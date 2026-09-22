$ErrorActionPreference = 'Continue'
$repo = 'C:\Users\loq\Documents\hackathons\JetBrains\CrunchGuard\plugin'
$asCfg = "$env:APPDATA\Google\AndroidStudio2024.3.2\plugins"
$z = Get-ChildItem "$repo\build\distributions\*.zip" | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $z) { Write-Output 'ZIP MISSING'; exit 1 }
$z = $z.FullName
Write-Output ("using zip: " + $z)

# verify the new classes are inside
$t = "$env:TEMP\cgcheck2"
Remove-Item $t -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Path $t | Out-Null
tar -xf $z -C $t
$j = (Get-ChildItem $t -Recurse -Filter *.jar)[0].FullName
$entries = tar -tf $j
$ok1 = ($entries | Select-String 'CrunchGuardEngine.class') -ne $null
$ok2 = ($entries | Select-String 'CrunchGuardSettings.class') -ne $null
$ok3 = ($entries | Select-String 'plugin.xml') -ne $null
Write-Output ("engine=" + $ok1 + " settings=" + $ok2 + " xml=" + $ok3)
if (-not ($ok1 -and $ok2 -and $ok3)) { Write-Output 'ABORT - classes missing'; exit 1 }

# close AS
Stop-Process -Name studio64 -Force -ErrorAction SilentlyContinue
Start-Sleep -Seconds 4
Write-Output 'AS closed'

# clean seed + extract correctly (zip contains top-level crunchguard-plugin/)
Remove-Item "$asCfg\crunchguard-plugin" -Recurse -Force -ErrorAction SilentlyContinue
if (Test-Path "$asCfg\crunchguard-plugin") { Write-Output 'REMOVE FAILED'; exit 1 }
tar -xf $z -C $asCfg
Get-ChildItem "$asCfg\crunchguard-plugin" -Recurse -File | ForEach-Object { '  ' + $_.FullName.Substring($asCfg.Length) + '  ' + $_.Length }

# refresh desktop zip
Copy-Item $z "$env:USERPROFILE\Desktop\" -Force
Write-Output 'desktop zip refreshed'

# relaunch AS with the test project
Start-Process 'C:\Program Files\Android\Android Studio\bin\studio64.exe' -ArgumentList '"C:\Users\loq\Documents\hackathons\JetBrains\CrunchGuard-IDE-test"'
Write-Output 'AS relaunching'
