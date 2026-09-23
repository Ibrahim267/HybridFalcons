$ErrorActionPreference = 'Continue'
$repo = 'C:\Users\loq\Documents\hackathons\JetBrains\CrunchGuard\plugin'
$z = "$repo\build\distributions\fitdeveloper-plugin-2.4.1.zip"
if (-not (Test-Path $z)) { Write-Output 'ZIP MISSING'; exit 1 }
Write-Output ("ZIP size: " + (Get-Item $z).Length)
$t = "$env:TEMP\cgcheck"
Remove-Item $t -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Path $t | Out-Null
tar -xf $z -C $t
$j = (Get-ChildItem $t -Recurse -Filter *.jar)[0].FullName
Write-Output ("JAR: " + $j + "  " + (Get-Item $j).Length + "B")
$entries = tar -tf $j
$hasEngine = ($entries | Select-String 'FitDeveloperEngine.class') -ne $null
$hasSettings = ($entries | Select-String 'FitDeveloperSettings.class') -ne $null
$hasXml = ($entries | Select-String 'META-INF/plugin.xml') -ne $null
$hasIdx = ($entries | Select-String 'web/index.html') -ne $null
Write-Output ("engine=" + $hasEngine + " settings=" + $hasSettings + " pluginXml=" + $hasXml + " webIndex=" + $hasIdx + " totalEntries=" + $entries.Count)
Write-Output '--- classes in jar ---'
$entries | Select-String '\.class' | ForEach-Object { '  ' + $_.Line }
