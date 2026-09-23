$ErrorActionPreference = 'Continue'
Stop-Process -Name studio64 -Force -ErrorAction SilentlyContinue
Start-Sleep -Seconds 3
$f = "$env:APPDATA\Google\AndroidStudio2024.3.2\options\other.xml"
if (Test-Path $f) {
  $enc = New-Object System.Text.UTF8Encoding($false)
  $c = [System.IO.File]::ReadAllText($f)
  $before = $c
  $c = $c.Replace('"fitdeveloper.enabled": "false"', '"fitdeveloper.enabled": "true"')
  if ($c -eq $before) { Write-Output 'WARN: no replacement made' }
  [System.IO.File]::WriteAllText($f, $c, $enc)
  Write-Output 'fitdeveloper.enabled -> true'
} else { Write-Output 'other.xml missing' }
Start-Process 'C:\Program Files\Android\Android Studio\bin\studio64.exe' -ArgumentList '"C:\Users\loq\Documents\hackathons\JetBrains\CrunchGuard-IDE-test"'
Write-Output 'AS relaunching with engine armed'
