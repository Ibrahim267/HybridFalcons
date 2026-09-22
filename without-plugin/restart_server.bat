@echo off
rem Restart CrunchGuard ONLY (safe: does not touch the bridge watch process)
rem Frees BOTH relay ports (HTTP 8787 + HTTPS 8788), then relaunches detached.
cd /d "%~dp0"
for /f "tokens=5" %%a in ('netstat -aon ^| findstr ":8787" ^| findstr "LISTENING"') do taskkill /f /pid %%a >nul 2>&1
for /f "tokens=5" %%a in ('netstat -aon ^| findstr ":8788" ^| findstr "LISTENING"') do taskkill /f /pid %%a >nul 2>&1
timeout /t 1 /nobreak >nul
start "CrunchGuard" /min cmd /c "node server.js > server.log 2>&1"
echo RESTARTED
exit /b 0
