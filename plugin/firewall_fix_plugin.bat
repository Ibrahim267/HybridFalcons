@echo off
rem ============================================================
rem  CrunchGuard PLUGIN - firewall fix (Windows)
rem  Opens TCP 8790-8795 so the phone can reach the relay that
rem  runs inside the IDE. Run once, as administrator.
rem ============================================================
net session >nul 2>&1
if %errorlevel% neq 0 (
  echo Requesting administrator rights...
  powershell -NoProfile -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
  exit /b 0
)

netsh advfirewall firewall delete rule name="CrunchGuard Plugin" >nul 2>&1
netsh advfirewall firewall add rule name="CrunchGuard Plugin" dir=in action=allow protocol=TCP localport=8790-8795
if %errorlevel% equ 0 (
  echo.
  echo   [OK] Ports 8790-8795 are open - the phone can now connect.
  echo        Restart the IDE so the plugin relay picks it up.
) else (
  echo   [FAIL] Could not add the firewall rule - add it manually.
)
echo.
pause
exit /b 0
