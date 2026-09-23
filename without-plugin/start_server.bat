@echo off
rem ============================================================
rem  FitDeveloper launcher - checks Node, starts the server
rem  detached (minimized window, logs to server.log), then
rem  opens the dashboard in the default browser.
rem ============================================================
cd /d "%~dp0"

where node >nul 2>nul
if errorlevel 1 (
  echo.
  echo   [FitDeveloper] Node.js is NOT installed on this PC.
  echo   Download the LTS installer from https://nodejs.org ,
  echo   install it, then run this file again.
  echo.
  start "" https://nodejs.org
  pause
  exit /b 1
)

start "FitDeveloper" /min cmd /c "node server.js > server.log 2>&1"
timeout /t 2 /nobreak >nul
start "" http://localhost:8787/

echo.
echo   ============================================================
echo     FitDeveloper is starting - dashboard opening in browser
echo     If the page does not load, wait 2s and refresh.
echo.
echo     PHONE SETUP: same Wi-Fi as this PC, then scan the QR
echo     shown when a break triggers.
echo     Phone cannot connect?  Right-click firewall_fix.bat
echo     and "Run as administrator" once.
echo.
echo     This window can be closed - server keeps running.
echo   ============================================================
echo.
timeout /t 6 /nobreak >nul
exit /b 0
