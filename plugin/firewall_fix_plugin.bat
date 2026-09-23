@echo off
rem ============================================================
rem  FitDeveloper PLUGIN - firewall fix (Windows) - TIGHT version
rem  Allows the phone to reach the relay inside the IDE, but ONLY:
rem    - on Private networks (your home Wi-Fi)
rem    - from devices on the SAME Wi-Fi (LocalSubnet)
rem  Internet traffic and public-Wi-Fi strangers stay blocked.
rem  To undo: run firewall_remove_plugin.bat
rem ============================================================
net session >nul 2>&1
if %errorlevel% neq 0 (
  echo Requesting administrator rights...
  powershell -NoProfile -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
  exit /b 0
)

netsh advfirewall firewall delete rule name="FitDeveloper Plugin" >nul 2>&1
netsh advfirewall firewall add rule name="FitDeveloper Plugin" dir=in action=allow protocol=TCP localport=8790-8795 profile=private remoteip=localsubnet
if %errorlevel% equ 0 (
  echo.
  echo   [OK] Rule added - scoped to Private networks + LocalSubnet only.
  echo.
  echo   NOTE: your Wi-Fi "ABA2" is currently marked PUBLIC in Windows,
  echo   so this rule will not apply until you mark it Private:
  echo     Settings - Network and Internet - Wi-Fi - ABA2 - Private network
  echo.
  echo   To close the ports again later: run firewall_remove_plugin.bat
) else (
  echo   [FAIL] Could not add the rule - add it manually.
)
echo.
pause
exit /b 0
