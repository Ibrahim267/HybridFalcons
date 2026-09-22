$ErrorActionPreference = 'Continue'
$log = "$env:LOCALAPPDATA\Google\AndroidStudio2024.3.2\log\idea.log"
$l = Get-Content $log -ErrorAction SilentlyContinue
Write-Output '--- [CrunchGuard] markers (last 6) ---'
$l | Select-String -SimpleMatch '[CrunchGuard]' | Select-Object -Last 6 | ForEach-Object { $_.Line }
Write-Output '--- plugin errors (last 4) ---'
$l | Select-String -Pattern 'ClassNotFoundException|NoClassDefFoundError|InstantiationException' | Select-String -SimpleMatch 'crunchguard' | Select-Object -Last 4 | ForEach-Object { $_.Line }
Write-Output '--- health ---'
try { (Invoke-WebRequest -UseBasicParsing -TimeoutSec 4 http://localhost:8790/api/health).Content } catch { Write-Output ('health FAIL: ' + $_.Exception.Message) }
Write-Output ''
Write-Output '--- ide-activity ---'
try { (Invoke-WebRequest -UseBasicParsing -TimeoutSec 4 http://localhost:8790/api/ide-activity).Content } catch { Write-Output ('ide-activity FAIL: ' + $_.Exception.Message) }
Write-Output ''
Write-Output '--- port ---'
(netstat -ano | Select-String ':8790' | Select-String 'LISTENING' | Select-Object -First 2) | ForEach-Object { $_.Line.Trim() }
