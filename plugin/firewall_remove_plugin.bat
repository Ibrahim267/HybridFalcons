@echo off
rem ============================================================
rem  FitDeveloper - firewall CLEANUP
rem  Deletes the "FitDeveloper Plugin" rule = closes ports 8790-8795.
rem  Run this after testing if you want the door shut again.
rem ============================================================
net session >nul 2>&1
if %errorlevel% neq 0 (
  echo Requesting administrator rights...
  powershell -NoProfile -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
  exit /b 0
)

netsh advfirewall firewall delete rule name="FitDeveloper Plugin"
echo.
echo   [OK] FitDeveloper firewall rule removed - ports closed again.
echo.
pause
exit /b 0
