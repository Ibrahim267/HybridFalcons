# ============================================================
#  FitDeveloper 2.5.0 redeploy - installs into Android Studio
#  AND IntelliJ IDEA, then relaunches both with the test project
# ============================================================
$ErrorActionPreference = 'Continue'
$repo   = 'C:\Users\loq\Documents\hackathons\JetBrains\CrunchGuard\plugin'
$zip    = "$repo\build\distributions\fitdeveloper-plugin-2.5.0.zip"
$testPrj= 'C:\Users\loq\Documents\hackathons\JetBrains\CrunchGuard-IDE-test'
$asCfg  = "$env:APPDATA\Google\AndroidStudio2024.3.2\plugins"
$iuCfg  = "$env:APPDATA\JetBrains\IntelliJIdea2026.2\plugins"
$report = "$repo\_deploy250_log.txt"

function Log($m) { Write-Output $m; Add-Content -Path $report -Value $m }

Set-Content -Path $report -Value "=== FitDeveloper 2.5.0 deploy $(Get-Date) ==="

# ---- 0. locate IntelliJ ----
$idea64 = $null
foreach ($c in @("C:\Program Files\JetBrains\IntelliJ IDEA 2026.2\bin\idea64.exe",
                 "C:\Program Files\JetBrains\IntelliJ IDEA\bin\idea64.exe")) {
  if (Test-Path $c) { $idea64 = $c; break }
}
if (-not $idea64) {
  $cands = Get-ChildItem 'C:\Program Files\JetBrains' -Filter idea64.exe -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1
  if ($cands) { $idea64 = $cands.FullName }
}
if (-not $idea64) {
  $lm = Get-ItemProperty 'HKLM:\Software\Microsoft\Windows\CurrentVersion\Uninstall\*',
                          'HKLM:\Software\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall\*',
                          'HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\*' -ErrorAction SilentlyContinue |
        Where-Object { $_.DisplayName -like 'IntelliJ IDEA*' -and $_.InstallLocation } | Select-Object -First 1
  if ($lm) { $idea64 = (Join-Path $lm.InstallLocation 'bin\idea64.exe') }
}
Log ("idea64.exe: " + $(if ($idea64 -and (Test-Path $idea64)) { $idea64 } else { 'NOT FOUND' }))

# ---- 1. verify the zip ----
if (-not (Test-Path $zip)) { Log 'ZIP MISSING'; exit 1 }
$t = "$env:TEMP\fdcheck250"
Remove-Item $t -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Path $t | Out-Null
tar -xf $zip -C $t
$j = (Get-ChildItem $t -Recurse -Filter *.jar | Select-Object -First 1).FullName
$entries = tar -tf $j
$hasEngine  = ($entries | Select-String 'FitDeveloperEngine.class') -ne $null
$hasFactory = ($entries | Select-String 'FitDeveloperToolWindowFactory.class') -ne $null
$hasLock    = ($entries | Select-String 'CodingLock.class') -ne $null
Log ("jar classes present: engine=" + $hasEngine + " factory=" + $hasFactory + " lock=" + $hasLock)
if (-not ($hasEngine -and $hasFactory -and $hasLock)) { Log 'ABORT - stale zip'; exit 1 }

# ---- 2. close both IDEs ----
foreach ($procName in @('studio64','idea64')) {
  $p = Get-Process $procName -ErrorAction SilentlyContinue
  if ($p) { Stop-Process -Name $procName -Force; Start-Sleep -Seconds 4; Log "$procName closed" }
  else    { Log "$procName was not running" }
}
Start-Sleep -Seconds 2

# ---- 3. seed plugin into both IDEs ----
# NOTE: extract the zip INTO the plugins root (the zip's root folder is
# 'fitdeveloper-plugin'). Extracting INTO a pre-created folder would nest it
# one level too deep and the IDE would never find lib\*.jar.
foreach ($cfg in @($asCfg, $iuCfg)) {
  if (-not (Test-Path $cfg)) { New-Item -ItemType Directory -Path $cfg -Force | Out-Null }
  Remove-Item "$cfg\fitdeveloper-plugin" -Recurse -Force -ErrorAction SilentlyContinue
  Remove-Item "$cfg\FitDeveloper" -Recurse -Force -ErrorAction SilentlyContinue
  tar -xf $zip -C $cfg
  $jar = "$cfg\fitdeveloper-plugin\lib\fitdeveloper-plugin-2.5.0.jar"
  if (Test-Path $jar) { Log ("seeded OK: " + $jar) }
  else { Log ("SEED FAILED - jar not at " + $jar) }
}

# ---- 4. desktop zip for manual Install-from-Disk ----
Copy-Item $zip "$env:USERPROFILE\Desktop\fitdeveloper-plugin-2.5.0.zip" -Force
Log 'desktop zip refreshed'

# ---- 5. relaunch both IDEs with the test project ----
Start-Process 'C:\Program Files\Android\Android Studio\bin\studio64.exe' -ArgumentList ('"' + $testPrj + '"')
Log 'Android Studio launching with CrunchGuard-IDE-test'
if ($idea64 -and (Test-Path $idea64)) {
  Start-Process $idea64 -ArgumentList ('"' + $testPrj + '"')
  Log 'IntelliJ IDEA launching with CrunchGuard-IDE-test'
} else {
  Log 'IntelliJ IDEA NOT launched - exe not found'
}
Log '=== deploy done ==='
