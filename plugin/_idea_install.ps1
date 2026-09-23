$ErrorActionPreference = 'Continue'
Write-Output '=== FitDeveloper -> IntelliJ IDEA install ==='

# locate newest IDEA install
$ideaExe = Get-ChildItem 'C:\Program Files\JetBrains\IntelliJ IDEA*\bin\idea64.exe' -ErrorAction SilentlyContinue |
  Sort-Object FullName -Descending | Select-Object -First 1
if (-not $ideaExe) {
  $ideaExe = Get-ChildItem "$env:LOCALAPPDATA\Programs\IntelliJ IDEA*\bin\idea64.exe" -ErrorAction SilentlyContinue |
    Sort-Object FullName -Descending | Select-Object -First 1
}
if (-not $ideaExe) { Write-Output 'NO IDEA INSTALL FOUND'; exit 1 }
Write-Output ('idea64.exe: ' + $ideaExe.FullName)

# locate newest IDEA config dir
$cfgRoot = Join-Path $env:APPDATA 'JetBrains'
$cfg = Get-ChildItem $cfgRoot -Directory -Filter 'IntelliJIdea*' -ErrorAction SilentlyContinue |
  Sort-Object Name -Descending | Select-Object -First 1
if (-not $cfg) { Write-Output 'NO IDEA CONFIG DIR'; exit 1 }
$plugins = Join-Path $cfg.FullName 'plugins'
New-Item -ItemType Directory -Force -Path $plugins | Out-Null
Write-Output ('config dir:  ' + $cfg.FullName)
Write-Output ('plugins dir: ' + $plugins)

Stop-Process -Name idea64 -Force -ErrorAction SilentlyContinue
Start-Sleep -Seconds 3

Get-ChildItem $plugins -Directory -ErrorAction SilentlyContinue |
  Where-Object { $_.Name -match '^(crunchguard|fitdeveloper)-plugin' } |
  ForEach-Object { Remove-Item $_.FullName -Recurse -Force -ErrorAction SilentlyContinue; Write-Output ('removed old: ' + $_.Name) }

$zip = 'C:\Users\loq\Documents\hackathons\JetBrains\CrunchGuard\plugin\build\distributions\fitdeveloper-plugin-2.4.1.zip'
tar -xf $zip -C $plugins
Write-Output 'seeded layout:'
Get-ChildItem $plugins -Directory |
  Where-Object { $_.Name -match '^(crunchguard|fitdeveloper)-plugin' } |
  ForEach-Object { Get-ChildItem $_.FullName -Recurse -File } |
  ForEach-Object { '  ' + $_.FullName.Substring($plugins.Length) + '  ' + $_.Length }

# pre-trust the test project so no trust dialog blocks startup
$proj = 'C:\Users\loq\Documents\hackathons\JetBrains\CrunchGuard-IDE-test'
$optDir = Join-Path $cfg.FullName 'options'
New-Item -ItemType Directory -Force -Path $optDir | Out-Null
$tp = Join-Path $optDir 'trusted-paths.xml'
$entry = '<entry key="' + $proj + '" value="true" />'
if (Test-Path $tp) {
  $content = Get-Content $tp -Raw
  if ($content -notlike ('*' + $proj + '*')) {
    if ($content -match '</map>') {
      $content = $content -replace '</map>', ('  ' + $entry + '`n      </map>')
    } elseif ($content -match '</component>') {
      $content = $content -replace '</component>', ('  <option name="TRUSTED_PROJECT_PATHS">`n        <map>' + $entry + '</map>`n      </option>`n    </component>')
    }
    Set-Content -Path $tp -Value $content -Encoding UTF8
    Write-Output 'trusted-paths.xml: entry added'
  } else { Write-Output 'trusted-paths.xml: already trusted' }
} else {
  $xml = '<?xml version="1.0" encoding="UTF-8"?>' + "`r`n" +
         '<application>' + "`r`n" +
         '  <component name="Trusted.Paths">' + "`r`n" +
         '    <option name="TRUSTED_PROJECT_PATHS">' + "`r`n" +
         '      <map>' + "`r`n" +
         '        ' + $entry + "`r`n" +
         '      </map>' + "`r`n" +
         '    </option>' + "`r`n" +
         '  </component>' + "`r`n" +
         '</application>'
  Set-Content -Path $tp -Value $xml -Encoding UTF8
  Write-Output 'trusted-paths.xml: created'
}
Write-Output 'IDEA SEEDED (use: start idea64.exe with project path)'
